package de.lenneflow.lenneflowterraformserver.util;

import de.lenneflow.lenneflowterraformserver.dto.ClusterDTO;
import de.lenneflow.lenneflowterraformserver.exception.PayloadNotValidException;


public class Validator {


    public static void validateCluster(ClusterDTO function) {
        checkMandatoryFields(function);
    }

    private static void checkMandatoryFields(ClusterDTO function) {
        if (function.getClusterName() == null || function.getClusterName().isEmpty()) {
            throw new PayloadNotValidException("Cluster Name is required");
        }
        if(function.getRegion() == null || function.getRegion().isEmpty()) {
            throw new PayloadNotValidException("Region is required");
        }
    }
}
