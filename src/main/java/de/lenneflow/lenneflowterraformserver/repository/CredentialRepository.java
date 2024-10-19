package de.lenneflow.lenneflowterraformserver.repository;


import de.lenneflow.lenneflowterraformserver.model.Credential;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CredentialRepository extends MongoRepository<Credential, String> {

    Credential findByUid(String uuid);

    Credential findByName(String name);

}
