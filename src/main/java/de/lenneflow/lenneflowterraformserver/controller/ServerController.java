package de.lenneflow.lenneflowterraformserver.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.lenneflow.lenneflowterraformserver.dto.ClusterDTO;
import de.lenneflow.lenneflowterraformserver.dto.TokenDTO;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
        mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);
    }


    @PostMapping("/cluster")
    public void createOrUpdateCluster(@RequestBody ClusterDTO clusterDTO) {
        Validator.validateCluster(clusterDTO);
        Cluster cluster = createDBTables(clusterDTO);
        Util.gitCloneOrUpdate(repositoryUrl, branch);
        String clusterDir = Util.getOrCreateClusterDir(clusterDTO.getCloudProvider(), clusterDTO.getClusterName(), clusterDTO.getRegion());
        Map<String, String> variablesMap = Util.createVariablesMap(clusterDTO);
        Util.createTfvarsFile(clusterDir, variablesMap);
        new Thread(() -> {
            try {
                updateClusterStatus(cluster, ClusterStatus.INITIALIZING);
                if(Util.runTerraformCommand("terraform init", clusterDir) != 0){
                    updateClusterStatus(cluster, ClusterStatus.ERROR);
                    throw new InternalServiceException("terraform init failed");
                }
                updateClusterStatus(cluster, ClusterStatus.PLANING);
                if(Util.runTerraformCommand("terraform plan", clusterDir) != 0){
                    updateClusterStatus(cluster, ClusterStatus.ERROR);
                    throw new InternalServiceException("terraform plan failed");
                }
                updateClusterStatus(cluster, ClusterStatus.CREATING);
                if(Util.runTerraformCommand("terraform apply -auto-approve", clusterDir) != 0){
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
    public void deleteCluster(@PathVariable String clusterName, @PathVariable String region, @PathVariable String cloudProvider) {
        String clusterDir = Util.getOrCreateClusterDir(CloudProvider.valueOf(cloudProvider), clusterName, region);
        Cluster cluster = clusterRepository.findByCloudProviderAndClusterNameAndRegion(CloudProvider.valueOf(cloudProvider.toUpperCase()), clusterName, region);
        new Thread(() -> {
            try {
                updateClusterStatus(cluster, ClusterStatus.PLANING_DELETE);
                if(Util.runTerraformCommand("terraform plan -destroy", clusterDir) != 0){
                    updateClusterStatus(cluster, ClusterStatus.ERROR);
                    throw new InternalServiceException("terraform plan failed");
                }

                updateClusterStatus(cluster, ClusterStatus.DELETING);
                if(Util.runTerraformCommand("terraform apply -destroy -auto-approve -input=false", clusterDir) != 0){
                    updateClusterStatus(cluster, ClusterStatus.ERROR);
                    throw new InternalServiceException("terraform destroy failed");
                }
                updateClusterStatus(cluster, ClusterStatus.DELETED);
                deleteDirectoryAndDBTables(clusterDir, cluster);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                throw new InternalServiceException(e.getMessage());
            }

        }).start();

    }



    @GetMapping("/access-token/provider/{cloudProvider}/cluster/{clusterName}/region/{region}")
    public String getConnectionToken(@PathVariable String clusterName, @PathVariable String region, @PathVariable String cloudProvider){
        Cluster cluster = clusterRepository.findByCloudProviderAndClusterNameAndRegion(CloudProvider.valueOf(cloudProvider.toUpperCase()), clusterName, region);
        AccessToken token = accessTokenRepository.findByUid(cluster.getAccessTokenId());

        if(token != null){
            if(token.getExpiration().isAfter(LocalDateTime.now())){
                return token.getToken();
            }
            else {
                accessTokenRepository.delete(token);
            }
        }
        try {
            switch (CloudProvider.valueOf(cloudProvider.toUpperCase())){
                case AWS -> {
                    Credential credential = credentialRepository.findByUid(cluster.getCredentialId());
                    Util.setCredentialsEnvironmentVariables(cluster.getCloudProvider(), credential);
                    String output = Util.runCmdCommandAndGetOutput("aws eks get-token --profile default --output json --cluster-name " + clusterName + " --region " + region);
                    TokenDTO tokenDTO = mapper.readValue(output.trim(), TokenDTO.class);
                    Map<String, String> statusNode = tokenDTO.getStatus();
                    AccessToken accessToken = new AccessToken();
                    accessToken.setUid(UUID.randomUUID().toString());
                    accessToken.setDescription("Access token for " + clusterName);
                    accessToken.setToken(statusNode.get("token"));
                    DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault());
                    ZonedDateTime zonedDateTime = ZonedDateTime.parse(statusNode.get("expirationTimestamp"), formatter);
                    accessToken.setExpiration(zonedDateTime.toLocalDateTime());
                    AccessToken savedToken = accessTokenRepository.save(accessToken);
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
            throw new InternalServiceException("get connection token failed \n" + e.getMessage());
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
