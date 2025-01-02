package de.lenneflow.lenneflowterraformserver.controller;

import de.lenneflow.lenneflowterraformserver.dto.ClusterDTO;
import de.lenneflow.lenneflowterraformserver.dto.NodeGroupDTO;
import de.lenneflow.lenneflowterraformserver.enums.CloudProvider;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Order(50)
class ServerControllerTest {

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private ClusterRepository clusterRepository;

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @InjectMocks
    private ServerController serverController;

    MockedStatic<Util> utilMockedStatic = mockStatic(Util.class);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        utilMockedStatic.when(() -> Util.initializeClusterDir(any(), any(), any())).thenReturn("");
        utilMockedStatic.when(() -> Util.gitClone(any(), any())).thenReturn("");
        utilMockedStatic.when(() -> Util.runCmdCommand(any())).thenReturn(0);
        utilMockedStatic.when(() -> Util.runCmdCommandAndGetOutput(any())).thenReturn("output");
        utilMockedStatic.when(() -> Util.runTerraformCommand(any(), any())).thenReturn(0);
        doNothing().when(accessTokenRepository).delete(any());
        doNothing().when(clusterRepository).delete(any());
    }

    @AfterEach
    void tearDown() {
        utilMockedStatic.close();
    }

    @Test
    void pingReturnsWorkingMessageWhenTerraformIsInstalled() throws IOException, InterruptedException {
        when(Util.runCmdCommand("terraform -help")).thenReturn(0);
        String response = serverController.ping();
        assertEquals("Lenneflow Kubernetes API Server is working", response);
    }

    @Test
    void pingReturnsErrorMessageWhenTerraformIsNotInstalled() throws IOException, InterruptedException {
        when(Util.runCmdCommand("terraform -help")).thenReturn(1);
        String response = serverController.ping();
        assertEquals("Terraform is not installed or is not working properly", response);
    }

    @Test
    void createOrUpdateClusterThrowsExceptionWhenClusterAlreadyExists() {
        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setCloudProvider(CloudProvider.AWS);
        clusterDTO.setClusterName("test-cluster");
        clusterDTO.setRegion("us-west-1");

        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(new Cluster());

        assertThrows(PayloadNotValidException.class, () -> serverController.createOrUpdateCluster(clusterDTO));
    }

    @Test
    void createOrUpdateClusterCreatesNewClusterWhenClusterDoesNotExist() {
        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setCloudProvider(CloudProvider.AWS);
        clusterDTO.setClusterName("test-cluster");
        clusterDTO.setRegion("us-west-1");

        Credential credential = new Credential();
        credential.setUid("test-uid");

        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(null);
        when(credentialRepository.save(any())).thenReturn(credential);
        when(credentialRepository.findByUid(any())).thenReturn(credential);
        when(clusterRepository.save(any())).thenReturn(new Cluster());

        Cluster cluster = serverController.createOrUpdateCluster(clusterDTO);

        assertNotNull(cluster);
        verify(clusterRepository, atLeast(1)).save(any());
    }

    //@Test
    void updateNodeGroupUpdatesClusterSuccessfully() {
        NodeGroupDTO nodeGroupDTO = new NodeGroupDTO();
        nodeGroupDTO.setCloudProvider(CloudProvider.AWS);
        nodeGroupDTO.setClusterName("test-cluster");
        nodeGroupDTO.setRegion("us-west-1");
        nodeGroupDTO.setMaximumNodeCount(2);

        Cluster cluster = new Cluster();
        cluster.setCloudProvider(CloudProvider.AWS);
        cluster.setClusterName("test-cluster");
        cluster.setRegion("us-west-1");

        Credential credential = new Credential();
        credential.setUid("test-uid");

        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(cluster);
        when(clusterRepository.save(any())).thenReturn(cluster);
        when(credentialRepository.save(any())).thenReturn(credential);
        when(credentialRepository.findByUid(any())).thenReturn(credential);

        Cluster updatedCluster = serverController.updateNodeGroup(nodeGroupDTO);

        assertNotNull(updatedCluster);
        verify(clusterRepository, timeout(500).atLeast(1)).save(any());
    }

    @Test
    void getClusterReturnsClusterWhenExists() {
        Cluster cluster = new Cluster();
        cluster.setCloudProvider(CloudProvider.AWS);
        cluster.setClusterName("test-cluster");
        cluster.setRegion("us-west-1");

        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(cluster);

        Cluster foundCluster = serverController.getCluster("test-cluster", "us-west-1", "AWS");

        assertNotNull(foundCluster);
        assertEquals("test-cluster", foundCluster.getClusterName());
    }

    @Test
    void getClusterReturnsNullWhenClusterDoesNotExist() {
        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(null);

        Cluster foundCluster = serverController.getCluster("test-cluster", "us-west-1", "AWS");

        assertNull(foundCluster);
    }

    @Test
    void getConnectionTokenReturnsTokenWhenExistsAndNotExpired() {
        Cluster cluster = new Cluster();
        cluster.setCloudProvider(CloudProvider.AWS);
        cluster.setClusterName("test-cluster");
        cluster.setRegion("us-west-1");

        AccessToken token = new AccessToken();
        token.setExpiration(LocalDateTime.now().plusDays(1));

        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(cluster);
        when(accessTokenRepository.findByUid(any())).thenReturn(token);

        AccessToken foundToken = serverController.getConnectionToken("test-cluster", "us-west-1", "AWS");

        assertNotNull(foundToken);
        assertEquals(token, foundToken);
    }

    @Test
    void getConnectionTokenThrowsExceptionWhenCloudProviderNotSupported() {
        Cluster cluster = new Cluster();
        cluster.setCloudProvider(CloudProvider.AZURE);
        cluster.setClusterName("test-cluster");
        cluster.setRegion("us-west-1");

        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(cluster);

        assertThrows(InternalServiceException.class, () -> serverController.getConnectionToken("test-cluster", "us-west-1", "AZURE"));
    }

    //@Test
    void createOrUpdateClusterValidatesClusterDTO() {
        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setCloudProvider(CloudProvider.AWS);
        clusterDTO.setClusterName("test-cluster");
        clusterDTO.setRegion("us-west-1");

        doThrow(new PayloadNotValidException("Invalid payload")).when(Validator.class);
        Validator.validateCluster(clusterDTO);

        assertThrows(PayloadNotValidException.class, () -> serverController.createOrUpdateCluster(clusterDTO));
    }

    @Test
    void deleteClusterThrowsExceptionWhenClusterNotFound() {
        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(null);
        assertThrows(InternalServiceException.class, () -> serverController.deleteCluster("test-cluster", "us-west-1", "AWS"));
    }

    @Test
    void getConnectionTokenDeletesExpiredToken() {
        Cluster cluster = new Cluster();
        cluster.setCloudProvider(CloudProvider.AWS);
        cluster.setClusterName("test-cluster");
        cluster.setRegion("us-west-1");

        AccessToken token = new AccessToken();
        token.setExpiration(LocalDateTime.now().minusDays(1));

        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(cluster);
        when(accessTokenRepository.findByUid(any())).thenReturn(token);

        assertThrows(InternalServiceException.class, () -> serverController.getConnectionToken("test-cluster", "us-west-1", "AWS"));
        verify(accessTokenRepository, times(1)).delete(token);
    }

    @Test
    void createOrUpdateClusterStartsNewThread() {
        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setCloudProvider(CloudProvider.AWS);
        clusterDTO.setClusterName("test-cluster");
        clusterDTO.setRegion("us-west-1");

        Cluster cluster = new Cluster();
        cluster.setClusterName("test-cluster");
        cluster.setUid("test-uid");

        Credential credential = new Credential();
        credential.setUid("test-uid");

        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(null);
        when(clusterRepository.save(any())).thenReturn(cluster, cluster);
        when(credentialRepository.save(any())).thenReturn(credential);

        serverController.createOrUpdateCluster(clusterDTO);

        verify(clusterRepository, atLeast(1)).save(any());
    }

    //@Test
    void updateNodeGroupStartsNewThread() {
        NodeGroupDTO nodeGroupDTO = new NodeGroupDTO();
        nodeGroupDTO.setCloudProvider(CloudProvider.AWS);
        nodeGroupDTO.setClusterName("test-cluster");
        nodeGroupDTO.setRegion("us-west-1");

        Cluster cluster = new Cluster();
        cluster.setCloudProvider(CloudProvider.AWS);
        cluster.setClusterName("test-cluster");
        cluster.setRegion("us-west-1");

        Credential credential = new Credential();
        credential.setUid("test-uid");

        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(cluster);
        when(credentialRepository.findByUid(any())).thenReturn(credential);
        when(clusterRepository.save(any())).thenReturn(cluster);
        when(clusterRepository.save(cluster)).thenReturn(cluster, cluster);
        mockStatic(Validator.class);
        doNothing().when(Validator.class);

        serverController.updateNodeGroup(nodeGroupDTO);

        verify(clusterRepository, timeout(100).atLeast(1)).save(any());
    }

    //@Test
    void deleteClusterStartsNewThread() {
        Cluster cluster = new Cluster();
        cluster.setCloudProvider(CloudProvider.AWS);
        cluster.setClusterName("test-cluster");
        cluster.setRegion("us-west-1");

        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(cluster, cluster);
        when(clusterRepository.save(cluster)).thenReturn(cluster, cluster);

        serverController.deleteCluster("test-cluster", "us-west-1", "AWS");
        verify(clusterRepository, timeout(100).atLeastOnce()).save(any());
    }

    @Test
    void getConnectionTokenStartsNewThread() {
        Cluster cluster = new Cluster();
        cluster.setCloudProvider(CloudProvider.AWS);
        cluster.setClusterName("test-cluster");
        cluster.setRegion("us-west-1");

        AccessToken token = new AccessToken();
        token.setExpiration(LocalDateTime.now().plusDays(1));

        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(any(), any(), any())).thenReturn(cluster);
        when(accessTokenRepository.findByUid(any())).thenReturn(token);

        serverController.getConnectionToken("test-cluster", "us-west-1", "AWS");

        verify(accessTokenRepository, times(1)).findByUid(any());
    }
}