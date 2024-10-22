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
public class OutputEntry {
    private String sensitive;
    private String type;
    private String value;
}
