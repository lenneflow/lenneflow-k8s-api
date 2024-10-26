package de.lenneflow.lenneflowterraformserver.util;

import de.lenneflow.lenneflowterraformserver.dto.ClusterDTO;
import de.lenneflow.lenneflowterraformserver.exception.PayloadNotValidException;
import de.lenneflow.lenneflowterraformserver.model.Cluster;


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
}
