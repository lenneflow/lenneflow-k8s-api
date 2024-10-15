package de.lenneflow.lenneflowterraformserver.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class VariableDTO {

    private String variableName;

    private String variableType;

    private String variableValue;
}
