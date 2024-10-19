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
public class NodeGroupDTO {

    private String clusterName;

    private String region;

    private CloudProvider cloudProvider;

    private int desiredNodeCount;

    private int minimumNodeCount;

    private int maximumNodeCount;

}
