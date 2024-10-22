package de.lenneflow.lenneflowterraformserver.dto;

import de.lenneflow.lenneflowterraformserver.enums.CloudProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ClusterDTO {

    private String clusterName;

    private String region;

    private String kubernetesVersion;

    private CloudProvider cloudProvider;

    private int desiredNodeCount;

    private int minimumNodeCount;

    private int maximumNodeCount;

    private String instanceType;

    private String amiType;

    private String accountId;

    private String accessKey;

    private String secretKey;


}
