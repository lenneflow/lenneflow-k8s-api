package de.lenneflow.lenneflowterraformserver.repository;


import de.lenneflow.lenneflowterraformserver.enums.CloudProvider;
import de.lenneflow.lenneflowterraformserver.model.Cluster;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClusterRepository extends MongoRepository<Cluster, String> {

    Cluster findByUid(String uuid);

    Cluster findByCloudProviderAndClusterNameAndRegion(CloudProvider cloudProvider, String clusterName, String region);

}
