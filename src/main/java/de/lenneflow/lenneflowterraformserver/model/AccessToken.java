package de.lenneflow.lenneflowterraformserver.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document
public class AccessToken {

    @Id
    private String uid;

    private String token;

    private String description;

    private LocalDateTime expiration;

    private LocalDateTime updated;
}
