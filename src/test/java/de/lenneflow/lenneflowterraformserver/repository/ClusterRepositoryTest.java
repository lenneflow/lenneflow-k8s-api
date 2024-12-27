package de.lenneflow.lenneflowterraformserver.repository;

import de.lenneflow.lenneflowterraformserver.enums.CloudProvider;
import de.lenneflow.lenneflowterraformserver.model.Cluster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Order(30)
class ClusterRepositoryTest {

    @Mock
    private ClusterRepository clusterRepository;

    @InjectMocks
    private ClusterRepositoryTest clusterRepositoryTest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void findByUidReturnsClusterWhenExists() {
        Cluster cluster = new Cluster();
        cluster.setUid("test-uid");

        when(clusterRepository.findByUid("test-uid")).thenReturn(cluster);

        Cluster foundCluster = clusterRepository.findByUid("test-uid");

        assertNotNull(foundCluster);
        assertEquals("test-uid", foundCluster.getUid());
    }

    @Test
    void findByUidReturnsNullWhenClusterDoesNotExist() {
        when(clusterRepository.findByUid("non-existent-uid")).thenReturn(null);

        Cluster foundCluster = clusterRepository.findByUid("non-existent-uid");

        assertNull(foundCluster);
    }

    @Test
    void findByCloudProviderAndClusterNameAndRegionReturnsClusterWhenExists() {
        Cluster cluster = new Cluster();
        cluster.setCloudProvider(CloudProvider.AWS);
        cluster.setClusterName("test-cluster");
        cluster.setRegion("us-west-1");

        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(CloudProvider.AWS, "test-cluster", "us-west-1")).thenReturn(cluster);

        Cluster foundCluster = clusterRepository.findByCloudProviderAndClusterNameAndRegion(CloudProvider.AWS, "test-cluster", "us-west-1");

        assertNotNull(foundCluster);
        assertEquals("test-cluster", foundCluster.getClusterName());
    }

    @Test
    void findByCloudProviderAndClusterNameAndRegionReturnsNullWhenClusterDoesNotExist() {
        when(clusterRepository.findByCloudProviderAndClusterNameAndRegion(CloudProvider.AWS, "non-existent-cluster", "us-west-1")).thenReturn(null);

        Cluster foundCluster = clusterRepository.findByCloudProviderAndClusterNameAndRegion(CloudProvider.AWS, "non-existent-cluster", "us-west-1");

        assertNull(foundCluster);
    }

}