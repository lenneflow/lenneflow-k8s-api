package de.lenneflow.lenneflowterraformserver.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TokenDTO {
    private String kind;
    private String apiVersion;
    private Map<String, String> spec;
    private Map<String, String> status;
}
