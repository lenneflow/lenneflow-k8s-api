package de.lenneflow.lenneflowterraformserver.util;

import de.lenneflow.lenneflowterraformserver.dto.ClusterDTO;
import de.lenneflow.lenneflowterraformserver.dto.NodeGroupDTO;
import de.lenneflow.lenneflowterraformserver.exception.PayloadNotValidException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatorTest {

    @Test
    void validateClusterThrowsExceptionWhenClusterNameIsNull() {
        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setClusterName(null);
        clusterDTO.setRegion("us-west-1");

        assertThrows(PayloadNotValidException.class, () -> Validator.validateCluster(clusterDTO));
    }

    @Test
    void validateClusterThrowsExceptionWhenClusterNameIsEmpty() {
        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setClusterName("");
        clusterDTO.setRegion("us-west-1");

        assertThrows(PayloadNotValidException.class, () -> Validator.validateCluster(clusterDTO));
    }

    @Test
    void validateClusterThrowsExceptionWhenRegionIsNull() {
        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setClusterName("test-cluster");
        clusterDTO.setRegion(null);

        assertThrows(PayloadNotValidException.class, () -> Validator.validateCluster(clusterDTO));
    }

    @Test
    void validateClusterThrowsExceptionWhenRegionIsEmpty() {
        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setClusterName("test-cluster");
        clusterDTO.setRegion("");

        assertThrows(PayloadNotValidException.class, () -> Validator.validateCluster(clusterDTO));
    }

    @Test
    void validateNodeGroupThrowsExceptionWhenClusterNameIsNull() {
        NodeGroupDTO nodeGroupDTO = new NodeGroupDTO();
        nodeGroupDTO.setClusterName(null);
        nodeGroupDTO.setRegion("us-west-1");
        nodeGroupDTO.setMaximumNodeCount(1);

        assertThrows(PayloadNotValidException.class, () -> Validator.validateNodeGroup(nodeGroupDTO));
    }

    @Test
    void validateNodeGroupThrowsExceptionWhenClusterNameIsEmpty() {
        NodeGroupDTO nodeGroupDTO = new NodeGroupDTO();
        nodeGroupDTO.setClusterName("");
        nodeGroupDTO.setRegion("us-west-1");
        nodeGroupDTO.setMaximumNodeCount(1);

        assertThrows(PayloadNotValidException.class, () -> Validator.validateNodeGroup(nodeGroupDTO));
    }

    @Test
    void validateNodeGroupThrowsExceptionWhenRegionIsNull() {
        NodeGroupDTO nodeGroupDTO = new NodeGroupDTO();
        nodeGroupDTO.setClusterName("test-cluster");
        nodeGroupDTO.setRegion(null);
        nodeGroupDTO.setMaximumNodeCount(1);

        assertThrows(PayloadNotValidException.class, () -> Validator.validateNodeGroup(nodeGroupDTO));
    }

    @Test
    void validateNodeGroupThrowsExceptionWhenRegionIsEmpty() {
        NodeGroupDTO nodeGroupDTO = new NodeGroupDTO();
        nodeGroupDTO.setClusterName("test-cluster");
        nodeGroupDTO.setRegion("");
        nodeGroupDTO.setMaximumNodeCount(1);

        assertThrows(PayloadNotValidException.class, () -> Validator.validateNodeGroup(nodeGroupDTO));
    }

    @Test
    void validateNodeGroupThrowsExceptionWhenMaximumNodeCountIsLessThanOne() {
        NodeGroupDTO nodeGroupDTO = new NodeGroupDTO();
        nodeGroupDTO.setClusterName("test-cluster");
        nodeGroupDTO.setRegion("us-west-1");
        nodeGroupDTO.setMaximumNodeCount(0);

        assertThrows(PayloadNotValidException.class, () -> Validator.validateNodeGroup(nodeGroupDTO));
    }
}