package de.lenneflow.lenneflowterraformserver.util;

import de.lenneflow.lenneflowterraformserver.dto.ClusterDTO;
import de.lenneflow.lenneflowterraformserver.dto.NodeGroupDTO;
import de.lenneflow.lenneflowterraformserver.exception.PayloadNotValidException;


public class Validator {

    public static void validateCluster(ClusterDTO function) {
        checkMandatoryFields(function);
    }

    private static void checkMandatoryFields(ClusterDTO clusterDTO) {
        if (clusterDTO.getClusterName() == null || clusterDTO.getClusterName().isEmpty()) {
            throw new PayloadNotValidException("Cluster Name is required");
        }
        if(clusterDTO.getRegion() == null || clusterDTO.getRegion().isEmpty()) {
            throw new PayloadNotValidException("Region is required");
        }
    }

    public static void validateNodeGroup(NodeGroupDTO nodeGroupDTO) {
        if (nodeGroupDTO.getClusterName() == null || nodeGroupDTO.getClusterName().isEmpty()) {
            throw new PayloadNotValidException("Cluster Name is required");
        }
        if(nodeGroupDTO.getRegion() == null || nodeGroupDTO.getRegion().isEmpty()) {
            throw new PayloadNotValidException("Region is required");
        }
        if(nodeGroupDTO.getMaximumNodeCount() < 1 ) {
            throw new PayloadNotValidException("Maximum Node Count should be greater than 0");
        }
    }
}
