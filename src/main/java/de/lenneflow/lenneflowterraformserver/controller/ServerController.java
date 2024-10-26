package de.lenneflow.lenneflowterraformserver.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.lenneflow.lenneflowterraformserver.dto.ClusterDTO;
import de.lenneflow.lenneflowterraformserver.dto.NodeGroupDTO;
import de.lenneflow.lenneflowterraformserver.dto.OutputDTO;
import de.lenneflow.lenneflowterraformserver.dto.TokenDTO;
import de.lenneflow.lenneflowterraformserver.enums.CloudProvider;
import de.lenneflow.lenneflowterraformserver.enums.ClusterStatus;
import de.lenneflow.lenneflowterraformserver.exception.InternalServiceException;
import de.lenneflow.lenneflowterraformserver.exception.PayloadNotValidException;
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
@RequestMapping("/api/kubernetes")
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

    /**
     * Create a new Cluster on the cloud
     */
    @PostMapping("/cluster/create")
    public Cluster createOrUpdateCluster(@RequestBody ClusterDTO clusterDTO) {
        Validator.validateCluster(clusterDTO);
        Cluster foundCluster = clusterRepository.findByCloudProviderAndClusterNameAndRegion( clusterDTO.getCloudProvider(), clusterDTO.getClusterName(), clusterDTO.getRegion());
        if (foundCluster != null) {
            throw new PayloadNotValidException("Cluster already exists");
        }
        Cluster cluster = createDBTables(clusterDTO);
        Util.gitCloneOrUpdate(repositoryUrl, branch);
        String clusterDir = Util.initializeClusterDir(clusterDTO.getCloudProvider(), clusterDTO.getClusterName(), clusterDTO.getRegion());
        Map<String, String> variablesMap = Util.createTfvarsVariablesMap(clusterDTO);
        Util.createTfvarsFile(clusterDir, variablesMap);
        new Thread(() -> executeTerraformCreationCommands(cluster, clusterDir, false, null)).start();
        return cluster;
    }

    @PostMapping("/cluster/update")
    public Cluster updateNodeGroup(@RequestBody NodeGroupDTO nodeGroupDTO) {
        String clusterDir = Util.initializeClusterDir(nodeGroupDTO.getCloudProvider(), nodeGroupDTO.getClusterName(), nodeGroupDTO.getRegion());
        Cluster cluster = clusterRepository.findByCloudProviderAndClusterNameAndRegion(nodeGroupDTO.getCloudProvider(), nodeGroupDTO.getClusterName(), nodeGroupDTO.getRegion());
        ClusterDTO clusterDTO = createClusterDTO(cluster, nodeGroupDTO);
        Map<String, String> variablesMap = Util.createTfvarsVariablesMap(clusterDTO);
        Util.createTfvarsFile(clusterDir, variablesMap);
        new Thread(() -> executeTerraformCreationCommands(cluster, clusterDir, true, nodeGroupDTO)).start();
        return cluster;
    }

    @GetMapping("/cluster/{clusterName}/provider/{cloudProvider}/region/{region}")
    public Cluster getCluster(@PathVariable String clusterName, @PathVariable String region, @PathVariable String cloudProvider) {
        return clusterRepository.findByCloudProviderAndClusterNameAndRegion(CloudProvider.valueOf(cloudProvider), clusterName, region);
    }


    @DeleteMapping("/cluster/{clusterName}/provider/{cloudProvider}/region/{region}")
    public void deleteCluster(@PathVariable String clusterName, @PathVariable String region, @PathVariable String cloudProvider) {
        String clusterDir = Util.initializeClusterDir(CloudProvider.valueOf(cloudProvider), clusterName, region);
        Cluster cluster = clusterRepository.findByCloudProviderAndClusterNameAndRegion(CloudProvider.valueOf(cloudProvider.toUpperCase()), clusterName, region);
        new Thread(() -> {
            try {
                updateClusterStatus(cluster, ClusterStatus.PLANING_DELETE);
                if (Util.runTerraformCommand("terraform plan -destroy", clusterDir) != 0) {
                    updateClusterStatus(cluster, ClusterStatus.ERROR);
                    throw new InternalServiceException("terraform plan failed");
                }

                updateClusterStatus(cluster, ClusterStatus.DELETING);
                if (Util.runTerraformCommand("terraform apply -destroy -auto-approve -input=false", clusterDir) != 0) {
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

    @GetMapping("/access-token/cluster/{clusterName}/provider/{cloudProvider}/region/{region}")
    public AccessToken getConnectionToken(@PathVariable String clusterName, @PathVariable String region, @PathVariable String cloudProvider) {
        Cluster cluster = clusterRepository.findByCloudProviderAndClusterNameAndRegion(CloudProvider.valueOf(cloudProvider.toUpperCase()), clusterName, region);
        AccessToken token = accessTokenRepository.findByUid(cluster.getAccessTokenId());

        if (token != null) {
            if (token.getExpiration().isAfter(LocalDateTime.now())) {
                return token;
            } else {
                //delete expired token
                accessTokenRepository.delete(token);
            }
        }
        try {
            switch (CloudProvider.valueOf(cloudProvider.toUpperCase())) {
                case AWS -> {
                    Credential credential = credentialRepository.findByUid(cluster.getCredentialId());
                    Util.setCredentialsEnvironmentVariables(cluster.getCloudProvider(), credential);
                    String output = Util.runCmdCommandAndGetOutput("aws eks get-token --profile default --output json --cluster-name " + clusterName + " --region " + region);
                    TokenDTO tokenDTO = mapper.readValue(output.trim(), TokenDTO.class);
                    Map<String, String> statusNode = tokenDTO.getStatus();
                    return createAndSaveAWSAccessToken(statusNode, cluster);
                }
                case AZURE -> throw new InternalServiceException("Azure cloud ist not yet supported");
                case GOOGLE -> throw new InternalServiceException("GOOGLE cloud ist not yet supported");
                default -> throw new InternalServiceException("Unexpected value: " + cloudProvider);
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServiceException("get connection token failed \n" + e.getMessage());
        }
    }

    private void updateClusterStatus(Cluster cluster, ClusterStatus clusterStatus) {
        cluster.setStatus(clusterStatus);
        clusterRepository.save(cluster);
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
        cluster.setStatus(ClusterStatus.NEW);
        cluster.setKubernetesVersion(clusterDTO.getKubernetesVersion());
        cluster.setMaximumNodeCount(clusterDTO.getMaximumNodeCount());
        cluster.setMinimumNodeCount(clusterDTO.getMinimumNodeCount());
        cluster.setDesiredNodeCount(clusterDTO.getDesiredNodeCount());
        return clusterRepository.save(cluster);
    }

    private void executeTerraformCreationCommands(Cluster cluster, String clusterDir, boolean isUpdate, NodeGroupDTO nodeGroup) {
        try {
            if(isUpdate){
                updateClusterStatus(cluster, ClusterStatus.INITIALIZING);
                if (Util.runTerraformCommand("terraform refresh", clusterDir) != 0) {
                    updateClusterStatus(cluster, ClusterStatus.ERROR);
                    throw new InternalServiceException("terraform refresh failed");
                }
            }else{
                if (Util.runTerraformCommand("terraform init", clusterDir) != 0) {
                    updateClusterStatus(cluster, ClusterStatus.ERROR);
                    throw new InternalServiceException("terraform init failed");
                }
            }
            updateClusterStatus(cluster, ClusterStatus.PLANING);
            Util.pause(2000);
            if (Util.runTerraformCommand("terraform plan", clusterDir) != 0) {
                updateClusterStatus(cluster, ClusterStatus.ERROR);
                throw new InternalServiceException("terraform plan failed");
            }
            updateClusterStatus(cluster, ClusterStatus.CREATING);
            Util.pause(2000);
            if (Util.runTerraformCommand("terraform apply -auto-approve", clusterDir) != 0) {
                updateClusterStatus(cluster, ClusterStatus.ERROR);
                throw new InternalServiceException("terraform apply failed");
            }
            updateClusterStatus(cluster, ClusterStatus.CREATED);
            updateClusterOutputData(cluster, clusterDir);
            if(isUpdate){
                cluster.setMaximumNodeCount(nodeGroup.getMaximumNodeCount());
                cluster.setMinimumNodeCount(nodeGroup.getMinimumNodeCount());
                cluster.setDesiredNodeCount(nodeGroup.getDesiredNodeCount());
                clusterRepository.save(cluster);
            }
        } catch (Exception e) {
            updateClusterStatus(cluster, ClusterStatus.ERROR);
            Thread.currentThread().interrupt();
            throw new InternalServiceException(e.getMessage());
        }
    }

    private void updateClusterOutputData(Cluster cluster, String clusterDir) {
        try {
            String jsonOutput = Util.runTerraformCommandAndGetOutput("terraform output -json", clusterDir);
            OutputDTO outputDTO = mapper.readValue(jsonOutput.trim(), OutputDTO.class);
            cluster.setApiServerEndpoint(outputDTO.getCluster_endpoint().getValue());
            cluster.setCaCertificate(outputDTO.getCluster_ca_certificate().getValue());
            clusterRepository.save(cluster);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            updateClusterStatus(cluster, ClusterStatus.ERROR);
            throw new InternalServiceException(e.getMessage());
        }


    }

    private void deleteDirectoryAndDBTables(String terraformDir, Cluster cluster) throws IOException {
        FileUtils.deleteDirectory(new File(terraformDir));
        if (accessTokenRepository.findByUid(cluster.getAccessTokenId()) != null) {
            accessTokenRepository.delete(accessTokenRepository.findByUid(cluster.getAccessTokenId()));
        }
        if (credentialRepository.findByUid(cluster.getCredentialId()) != null) {
            credentialRepository.delete(credentialRepository.findByUid(cluster.getCredentialId()));
        }
        clusterRepository.delete(cluster);
    }

    private AccessToken createAndSaveAWSAccessToken(Map<String, String> statusNode, Cluster cluster) {
        AccessToken accessToken = new AccessToken();
        accessToken.setUid(UUID.randomUUID().toString());
        accessToken.setDescription("Access token for " + cluster.getClusterName());
        accessToken.setToken(statusNode.get("token"));
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault());
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(statusNode.get("expirationTimestamp"), formatter);
        accessToken.setExpiration(zonedDateTime.toLocalDateTime());
        AccessToken savedToken = accessTokenRepository.save(accessToken);
        cluster.setAccessTokenId(savedToken.getUid());
        clusterRepository.save(cluster);
        return savedToken;
    }

    private ClusterDTO createClusterDTO(Cluster cluster, NodeGroupDTO nodeGroupDTO) {
        Credential credential = credentialRepository.findByUid(cluster.getCredentialId());
        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setClusterName(cluster.getClusterName());
        clusterDTO.setRegion(cluster.getRegion());
        clusterDTO.setCloudProvider(cluster.getCloudProvider());
        clusterDTO.setAccessKey(credential.getAccessKey());
        clusterDTO.setAmiType(cluster.getAmiType());
        clusterDTO.setInstanceType(cluster.getInstanceType());
        clusterDTO.setSecretKey(credential.getSecretKey());
        clusterDTO.setKubernetesVersion(cluster.getKubernetesVersion());
        clusterDTO.setDesiredNodeCount(nodeGroupDTO.getDesiredNodeCount());
        clusterDTO.setMaximumNodeCount(nodeGroupDTO.getMaximumNodeCount());
        clusterDTO.setMinimumNodeCount(nodeGroupDTO.getMinimumNodeCount());
        return clusterDTO;
    }

}
