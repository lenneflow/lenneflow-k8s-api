package de.lenneflow.lenneflowterraformserver.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.lenneflow.lenneflowterraformserver.dto.ClusterDTO;
import de.lenneflow.lenneflowterraformserver.enums.CloudProvider;
import de.lenneflow.lenneflowterraformserver.enums.ClusterStatus;
import de.lenneflow.lenneflowterraformserver.exception.InternalServiceException;
import de.lenneflow.lenneflowterraformserver.model.AccessToken;
import de.lenneflow.lenneflowterraformserver.model.Cluster;
import de.lenneflow.lenneflowterraformserver.model.Credential;
import de.lenneflow.lenneflowterraformserver.repository.AccessTokenRepository;
import de.lenneflow.lenneflowterraformserver.repository.ClusterRepository;
import de.lenneflow.lenneflowterraformserver.repository.CredentialRepository;
import de.lenneflow.lenneflowterraformserver.util.Util;
import de.lenneflow.lenneflowterraformserver.util.Validator;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/terraform")
public class ServerController {

    @Value("${git.repository.url}")
    private String repositoryUrl;

    @Value("${git.repository.branch}")
    private String branch;

    private final CredentialRepository credentialRepository;
    private final ClusterRepository clusterRepository;
    private final AccessTokenRepository accessTokenRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public ServerController(CredentialRepository credentialRepository, ClusterRepository clusterRepository, AccessTokenRepository accessTokenRepository) {
        this.credentialRepository = credentialRepository;
        this.clusterRepository = clusterRepository;
        this.accessTokenRepository = accessTokenRepository;
    }


    @PostMapping("/cluster")
    public void createOrUpdateCluster(@RequestBody ClusterDTO clusterDTO) {
        Validator.validateCluster(clusterDTO);
        Cluster cluster = createDBTables(clusterDTO);
        String clusterDir = Util.gitCloneOrUpdate(repositoryUrl, branch, clusterDTO.getClusterName(), clusterDTO.getRegion());
        String terraformDir = clusterDir + File.separator + Util.getTerraformSubDir(clusterDTO.getCloudProvider());
        Map<String, String> variablesMap = Util.createVariablesMap(clusterDTO);
        Util.createTfvarsFile(terraformDir, variablesMap);
        new Thread(() -> {
            try {
                updateClusterStatus(cluster, ClusterStatus.INITIALIZING);
                if(Util.runTerraformCommand("terraform init", terraformDir) != 0){
                    updateClusterStatus(cluster, ClusterStatus.ERROR);
                    throw new InternalServiceException("terraform init failed");
                }
                updateClusterStatus(cluster, ClusterStatus.PLANING);
                if(Util.runTerraformCommand("terraform plan", terraformDir) != 0){
                    updateClusterStatus(cluster, ClusterStatus.ERROR);
                    throw new InternalServiceException("terraform plan failed");
                }
                updateClusterStatus(cluster, ClusterStatus.CREATING);
                if(Util.runTerraformCommand("terraform apply -auto-approve", terraformDir) != 0){
                    updateClusterStatus(cluster, ClusterStatus.ERROR);
                    throw new InternalServiceException("terraform apply failed");
                }
                updateClusterStatus(cluster, ClusterStatus.CREATED);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                updateClusterStatus(cluster, ClusterStatus.ERROR);
                throw new InternalServiceException(e.getMessage());
            }
        }).start();
    }

    @DeleteMapping("/provider/{cloudProvider}/cluster/{clusterName}/region/{region}")
    public void deleteCluster(@PathVariable String clusterName, @PathVariable String region, @PathVariable CloudProvider cloudProvider) {
        String terraformDir = Util.getTerraformClusterDir(clusterName, region) + File.separator + Util.getTerraformSubDir(cloudProvider);
        Cluster cluster = clusterRepository.findByCloudProviderAndClusterNameAndRegion(cloudProvider, clusterName, region);
        new Thread(() -> {
            try {
                updateClusterStatus(cluster, ClusterStatus.PLANING_DELETE);
                if(Util.runTerraformCommand("terraform plan -destroy", terraformDir) != 0){
                    updateClusterStatus(cluster, ClusterStatus.ERROR);
                    throw new InternalServiceException("terraform plan failed");
                }
                if(Util.runTerraformCommand("terraform apply -destroy -auto-approve -input=false", terraformDir) != 0){
                    updateClusterStatus(cluster, ClusterStatus.ERROR);
                    throw new InternalServiceException("terraform destroy failed");
                }
                updateClusterStatus(cluster, ClusterStatus.DELETED);
                deleteDirectoryAndDBTables(terraformDir, cluster);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                throw new InternalServiceException(e.getMessage());
            }

        }).start();

    }



    @GetMapping("/access-token/provider/{cloudProvider}/cluster/{clusterName}/region/{region}")
    public String getConnectionToken(@PathVariable String clusterName, @PathVariable String region, @PathVariable CloudProvider cloudProvider){
        Cluster cluster = clusterRepository.findByCloudProviderAndClusterNameAndRegion(cloudProvider, clusterName, region);
        AccessToken token = accessTokenRepository.findByUid(cluster.getAccessTokenId());
        if(token != null && token.getExpiration().isAfter(LocalDateTime.now())){
            return token.getToken();
        }
        try {
            switch (cloudProvider){
                case AWS -> {
                    Util.setCredentialsEnvironmentVariables(cluster.getCloudProvider(), credentialRepository.findByUid(cluster.getCredentialId()));
                    String output = Util.runCmdCommandAndGetOutput("aws eks get-token --output json --cluster-name " + clusterName + " --region " + region);
                    JsonNode node = mapper.readTree(output);
                    JsonNode statusNode = node.get("status");
                    AccessToken accessToken = new AccessToken();
                    accessToken.setUid(UUID.randomUUID().toString());
                    accessToken.setDescription("Access token for " + clusterName);
                    accessToken.setToken(statusNode.get("token").toString());
                    accessToken.setExpiration(LocalDateTime.parse(statusNode.get("expirationTimestamp").toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));
                    AccessToken savedToken =accessTokenRepository.save(accessToken);
                    cluster.setAccessTokenId(savedToken.getUid());
                    clusterRepository.save(cluster);
                    return savedToken.getToken();
                }
                case AZURE -> {
                    Util.setCredentialsEnvironmentVariables(cluster.getCloudProvider(), credentialRepository.findByUid(cluster.getCredentialId()));
                    return Util.runCmdCommandAndGetOutput("az aks get-token --cluster-name " + clusterName + " --region " + region);
                }
                default -> throw new InternalServiceException("Unexpected value: " + cloudProvider);
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServiceException("get connection token failed");
        }
    }

    private void updateClusterStatus(Cluster cluster, ClusterStatus clusterStatus) {
        Cluster found = clusterRepository.findByUid(cluster.getUid());
        found.setStatus(clusterStatus);
        clusterRepository.save(found);
    }

    private Cluster createDBTables(ClusterDTO clusterDTO) {
        Credential credential = new Credential();
        credential.setUid(UUID.randomUUID().toString());
        credential.setName(clusterDTO.getClusterName() + "_credential");
        credential.setDescription("This is a credential for " + clusterDTO.getClusterName());
        credential.setAccessKey(clusterDTO.getAccessKey());
        credential.setSecretKey(clusterDTO.getSecretKey());
        credential.setCreated(LocalDateTime.now());
        credential.setUpdated(LocalDateTime.now());
        Credential savedCredential = credentialRepository.save(credential);

        Cluster cluster = new Cluster();
        cluster.setUid(UUID.randomUUID().toString());
        cluster.setClusterName(clusterDTO.getClusterName());
        cluster.setRegion(clusterDTO.getRegion());
        cluster.setCloudProvider(clusterDTO.getCloudProvider());
        cluster.setAmiType(clusterDTO.getAmiType());
        cluster.setCredentialId(savedCredential.getUid());
        cluster.setInstanceType(clusterDTO.getInstanceType());
        cluster.setStatus(ClusterStatus.CREATING);
        cluster.setKubernetesVersion(clusterDTO.getKubernetesVersion());
        cluster.setMaximumNodeCount(clusterDTO.getMaximumNodeCount());
        cluster.setMinimumNodeCount(clusterDTO.getMinimumNodeCount());
        cluster.setDesiredNodeCount(clusterDTO.getDesiredNodeCount());
        return clusterRepository.save(cluster);
    }

    private void deleteDirectoryAndDBTables(String terraformDir, Cluster cluster) throws IOException {
        FileUtils.deleteDirectory(new File(terraformDir));
        if(accessTokenRepository.findByUid(cluster.getAccessTokenId()) != null){
            accessTokenRepository.delete(accessTokenRepository.findByUid(cluster.getAccessTokenId()));
        }
        if(credentialRepository.findByUid(cluster.getCredentialId()) != null){
            credentialRepository.delete(credentialRepository.findByUid(cluster.getCredentialId()));
        }
        clusterRepository.delete(cluster);
    }

}
