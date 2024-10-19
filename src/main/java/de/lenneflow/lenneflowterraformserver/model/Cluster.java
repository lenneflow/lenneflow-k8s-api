package de.lenneflow.lenneflowterraformserver.model;

import de.lenneflow.lenneflowterraformserver.enums.CloudProvider;
import de.lenneflow.lenneflowterraformserver.enums.ClusterStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Document
public class Cluster {

    @Id
    private String uid;

    private String clusterName;

    private String region;

    private String kubernetesVersion;

    private CloudProvider cloudProvider;

    private ClusterStatus status;

    private String credentialId;

    private int desiredNodeCount;

    private int minimumNodeCount;

    private int maximumNodeCount;

    private String instanceType;

    private String amiType;

    private String accessTokenId;

}
