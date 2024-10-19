package de.lenneflow.lenneflowterraformserver.util;

import de.lenneflow.lenneflowterraformserver.dto.ClusterDTO;
import de.lenneflow.lenneflowterraformserver.enums.CloudProvider;
import de.lenneflow.lenneflowterraformserver.exception.InternalServiceException;
import de.lenneflow.lenneflowterraformserver.model.Credential;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class Util {

    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    private Util(){}

    public static String getTerraformBaseDir(){
        String baseDir = System.getProperty("user.home")+ File.separator + "Terraform";
        if(!new File(baseDir).exists() && !new File(baseDir).mkdirs()){
            throw new InternalServiceException("Unable to create directory " + baseDir);
        }
        return  baseDir;
    }

    public static String getTerraformClusterDir(String clusterName, String region){
        String  clusterDirPath = getTerraformBaseDir() + File.separator + region + File.separator + clusterName;
        if(!new File(clusterDirPath).exists() && !new File(clusterDirPath).mkdirs()){
            throw new InternalServiceException("Unable to create directory " + clusterDirPath);
        }
        return  clusterDirPath;
    }

    public static String getTerraformSubDir(CloudProvider cloudProvider) {
        switch(cloudProvider){
            case AWS -> {
                return "aws";
            }
            case AZURE -> {
                return "azure";
            }
            case GOOGLE -> {
                return "google";
            }
            default -> throw new InternalServiceException("Unknown cloud provider " + cloudProvider);

        }
    }


    public static String gitCloneOrUpdate(String repositoryUrl, String branch, String clusterName, String region){
        try {
            String clusterDirPath = getTerraformClusterDir(clusterName, region);
            if(FileUtils.isEmptyDirectory(new File(clusterDirPath))){
                Git.cloneRepository().setURI(repositoryUrl).setBranch(branch).setDirectory(new File(clusterDirPath)).call();
            }else{
                new Git(new FileRepository(clusterDirPath)).pull();
            }
            return clusterDirPath;
        } catch (Exception e) {
            throw new InternalServiceException(e.getMessage());
        }
    }

    public static int runCmdCommand(String command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command.split(" "));
        Process process = processBuilder.start();
        return process.waitFor();
    }

    public static String runCmdCommandAndGetOutput(String command) throws IOException, InterruptedException {
        StringBuilder output = new StringBuilder();
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command.split(" "));
        Process process = processBuilder.start();
        // Capture the command output
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        int exitCode = process.waitFor();
        if(exitCode != 0){
            throw new IOException("Exit code: " + exitCode);
        }
        return output.toString();
    }


    // Method to execute Terraform commands
    public static int runTerraformCommand(String command, String terraformFilesPath) throws IOException, InterruptedException {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(command.split(" "));

            // Set the Terraform working directory (replace with your directory)
            processBuilder.directory(new java.io.File(terraformFilesPath));
            Process process = processBuilder.start();

            // Capture the command output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info(line);
            }
            // Wait for the process to complete
            int exitCode = process.waitFor();
            logger.info("Command executed with exit code: {}", exitCode);
            return exitCode;


    }

    public static Map<String, String> createVariablesMap(ClusterDTO clusterDTO) {
        Map<String, String> variables = new HashMap<>();
        variables.put("cluster_name", clusterDTO.getClusterName());
        variables.put("region", clusterDTO.getRegion());
        variables.put("cluster_version", clusterDTO.getKubernetesVersion());
        variables.put("node_group_desired_size", clusterDTO.getDesiredNodeCount() + "");
        variables.put("node_group_min_size", clusterDTO.getMinimumNodeCount() + "");
        variables.put("node_group_max_size", clusterDTO.getMaximumNodeCount() + "");
        variables.put("instance_type", clusterDTO.getInstanceType());
        variables.put("ami_type", clusterDTO.getAmiType());
        variables.put("aws_access_key", clusterDTO.getAccessKey());
        variables.put("aws_secret_key", clusterDTO.getSecretKey());
        return variables;

    }


    public static void createTfvarsFile(String dirPath, Map<String, String> variables) {
        File dir = new File(dirPath);
        dir.setWritable(true);
        dir.setExecutable(true);
        String filePath = dirPath + File.separator + "terraform.tfvars";
        try (FileWriter writer = new FileWriter(filePath)) {
            // Iterate through the variables and write each key-value pair in the correct format
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                writer.write(entry.getKey() + " = " + "\"" +  entry.getValue() + "\"" + "\n");
            }
            logger.info(".tfvars file created successfully at {}", filePath);
        } catch (IOException e) {
            logger.error("Error writing .tfvars file: {}", e.getMessage());
        }
    }

    public static void setCredentialsEnvironmentVariables(CloudProvider cloudProvider, Credential credential) {
        try {
            switch(cloudProvider){
                case AWS -> {
                    runCmdCommand("aws configure set acces_key_id " + credential.getAccessKey());
                    runCmdCommand("aws configure set secret_acces_key " + credential.getSecretKey());
                }
                case AZURE -> {
                    runCmdCommand("az login --service-principal --username " +credential.getAccessKey() + " --password " + credential.getSecretKey() + " --tenant " +credential.getAccountId());
                }
                default -> throw new InternalServiceException("Unsupported cloud provider " + cloudProvider);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServiceException(e.getMessage());
        }

    }


}
