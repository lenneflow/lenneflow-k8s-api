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
public class OutputDTO {
    private OutputEntry cluster_endpoint;
    private OutputEntry cluster_ca_certificate;
    private OutputEntry cluster_security_group_id;
    private OutputEntry region;
    private OutputEntry cluster_name;

}
