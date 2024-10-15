package de.lenneflow.lenneflowterraformserver.controller;

import de.lenneflow.lenneflowterraformserver.dto.ClusterDTO;
import de.lenneflow.lenneflowterraformserver.dto.VariableDTO;
import de.lenneflow.lenneflowterraformserver.exception.InternalServiceException;
import de.lenneflow.lenneflowterraformserver.util.Util;
import de.lenneflow.lenneflowterraformserver.util.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/terraform")
public class ApiController {

    @Value("${git.repository.url}")
    private String repositoryUrl;

    @Value("${git.repository.branch}")
    private String branch;

    @Value("${terraform.aws.create}")
    private String createFolderPath;

    @GetMapping(value = {"/check"})
    public String checkService() {
        return "Welcome to the Terraform API Service! Everything is working fine!";
    }

    @PostMapping("/variable")
    public void setVariable(@RequestBody VariableDTO variableDTO) {

        try {
            switch (variableDTO.getVariableType()) {
                case "credential" ->
                        Util.runCmdCommand("aws configure set " + variableDTO.getVariableName().toLowerCase() + " " + variableDTO.getVariableValue());
                case "env" ->
                        Util.runCmdCommand("setx " + variableDTO.getVariableName() + "=" + variableDTO.getVariableValue());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


    }

    @PostMapping("/cluster")
    public void createCluster(@RequestBody ClusterDTO clusterDTO) {
        Validator.validateCluster(clusterDTO);
        String clusterDir = Util.gitCloneOrUpdate(repositoryUrl, branch, clusterDTO.getClusterName(), clusterDTO.getRegion());
        String terraformDir = Util.getTerraformSubDir(clusterDir, createFolderPath);
        Map<String, String> variablesMap = Util.createVariablesMap(clusterDTO);
        Util.createTfvarsFile(terraformDir, variablesMap);
        try {
            if(Util.runTerraformCommand("terraform init", terraformDir) != 0){
                throw new InternalServiceException("terraform init failed");
            }
            if(Util.runTerraformCommand("terraform plan", terraformDir) != 0){
                throw new InternalServiceException("terraform plan failed");
            }
            if(Util.runTerraformCommand("terraform apply -auto-approve", terraformDir) != 0){
                throw new InternalServiceException("terraform apply failed");
            }
        } catch (Exception e) {
            throw new InternalServiceException(e.getMessage());
        }
        //new Thread(() -> {

        //}).start();
    }

    @DeleteMapping("/cluster/{clusterName}/region/{region}")
    public void deleteCluster(@PathVariable String clusterName, @PathVariable String region) {
        String terraformDir = Util.getTerraformSubDir(Util.getTerraformClusterDir(clusterName, region), createFolderPath);
        try {
            if(Util.runTerraformCommand("terraform plan -destroy", terraformDir) != 0){
                throw new InternalServiceException("terraform plan failed");
            }
            if(Util.runTerraformCommand("terraform apply -destroy -auto-approve -input=false", terraformDir) != 0){
                throw new InternalServiceException("terraform destroy failed");
            }
            new File(terraformDir).delete();
        } catch (Exception e) {
            throw new InternalServiceException(e.getMessage());
        }
        //new Thread(() -> {

        //}).start();

    }
}
