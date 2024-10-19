package de.lenneflow.lenneflowterraformserver.repository;


import de.lenneflow.lenneflowterraformserver.model.AccessToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AccessTokenRepository extends MongoRepository<AccessToken, String> {

    AccessToken findByUid(String uuid);

}
