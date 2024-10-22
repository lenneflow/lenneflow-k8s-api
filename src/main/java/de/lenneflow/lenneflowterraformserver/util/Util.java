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

    /**
     * This function return the base dir of all terraform files that will be used to manage
     * the kubernetes infrastructure
     * @return the path of the directory
     */
    public static String getBaseDir(){
        String baseDir = System.getProperty("user.home")+ File.separator + "Lenneflow";
        if(!new File(baseDir).exists() && !new File(baseDir).mkdirs()){
            throw new InternalServiceException("Unable to create directory " + baseDir);
        }
        return  baseDir;
    }

    /**
     * To terraform files needed to create a new kubernetes cluster will be saved in a separate directory. In this directory the state
     * of the created infrastructure will also be saved. This function creates this directory and return the path.
     * @param cloudProvider
     * @param clusterName
     * @param region
     * @return the directory path
     */
    public static String getClusterDir(CloudProvider cloudProvider, String clusterName, String region){
        String  clusterDirPath = getBaseDir() + File.separator + cloudProvider.toString().toLowerCase() + File.separator + region + File.separator + clusterName;
        if(!new File(clusterDirPath).exists() && !new File(clusterDirPath).mkdirs()){
            throw new InternalServiceException("Unable to create directory " + clusterDirPath);
        }
        return  clusterDirPath;
    }

    /**
     * This function will clone the terraform files from GitHub. If the files are already cloned,
     * it will do a pull to get the all the changes.
     * @param repositoryUrl
     * @param branch
     * @return the path of the directory
     */
    public static String gitCloneOrUpdate(String repositoryUrl, String branch){
        try {
            String terraformDir = getBaseDir() + File.separator + "Terraform";
            if(!new File(terraformDir).exists() && new File(terraformDir).mkdirs()){
                Git.cloneRepository().setURI(repositoryUrl).setBranch(branch).setDirectory(new File(terraformDir)).call();
            }else{
                Git git = new Git(new FileRepository(terraformDir));
                git.pull();
                git.close();
            }
            return terraformDir;
        } catch (Exception e) {
            throw new InternalServiceException(e.getMessage());
        }
    }

    /**
     * Copies the needed terraform files to the cluster directory if it is empty.
     * @param cloudProvider
     * @param clusterName
     * @param region
     * @return the path of the cluster directory
     */
    public static String initializeClusterDir(CloudProvider cloudProvider, String clusterName, String region){
        try {
            String terraformDir = getBaseDir() + File.separator + "Terraform";
            String clusterDir = getClusterDir(cloudProvider, clusterName, region);
            if(FileUtils.isEmptyDirectory(new File(clusterDir))){
                switch(cloudProvider){
                    case AWS -> {
                        String source = terraformDir + File.separator + "aws";
                        FileUtils.copyDirectory(new File(source), new File(clusterDir));
                    }
                    case AZURE -> {
                        String source = terraformDir + File.separator + "azure";
                        FileUtils.copyDirectory(new File(source), new File(clusterDir));
                    }
                    case GOOGLE -> {
                        String source = terraformDir + File.separator + "google";
                        FileUtils.copyDirectory(new File(source), new File(clusterDir));
                    }
                    default -> throw new InternalServiceException("Unknown cloud provider " + cloudProvider);

                }
            }
            return clusterDir;

        } catch (Exception e) {
            throw new InternalServiceException(e.getMessage());
        }
    }

    /**
     * Runs a command line command and return the exit code
     * @param command
     * @return exit code
     * @throws IOException
     * @throws InterruptedException
     */
    public static int runCmdCommand(String command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command.split(" "));
        Process process = processBuilder.start();
        return process.waitFor();
    }

    /**
     * Runs a command line command and return the output of the run.
     * @param command
     * @return String output of run
     * @throws IOException
     * @throws InterruptedException
     */
    public static String runCmdCommandAndGetOutput(String command) throws IOException, InterruptedException {
        StringBuilder output = new StringBuilder();
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command.split(" "));

        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        int exitCode = process.waitFor();
        if(exitCode != 0){
            throw new IOException("Exit code: " + exitCode + "\n" + process.getErrorStream().toString());
        }
        return output.toString();
    }


    /**
     * Executes a terraform command line command
     * @param command
     * @param terraformFilesPath
     * @return the exit code
     * @throws IOException
     * @throws InterruptedException
     */
    public static int runTerraformCommand(String command, String terraformFilesPath) throws IOException, InterruptedException {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(command.split(" "));

            // Set the Terraform working directory (replace with your directory)
            processBuilder.directory(new File(terraformFilesPath));
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

    /**
     * This method creates a map containing all variables and the values for AWS kubernetes.
     * The map will be used to create the tfvars file.
     * @param clusterDTO
     * @return the variables map
     */
    public static Map<String, String> createTfvarsVariablesMap(ClusterDTO clusterDTO) {
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


    /**
     * Uses a variables map to create the tfvars file.
     * @param dirPath
     * @param variables
     */
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

    /**
     * In order to run some command line commands, it is necessary to set the credentials as environment variables
     * This method does the job
     * @param cloudProvider
     * @param credential
     */
    public static void setCredentialsEnvironmentVariables(CloudProvider cloudProvider, Credential credential) {
        try {
            switch(cloudProvider){
                case AWS -> {
                    runCmdCommand("cmd.exe /c aws configure set aws_access_key_id " + credential.getAccessKey());
                    runCmdCommand("cmd.exe /c aws configure set aws_secret_access_key " + credential.getSecretKey());
                }
                case AZURE -> runCmdCommand("az login --service-principal --username " +credential.getAccessKey() + " --password " + credential.getSecretKey() + " --tenant " +credential.getAccountId());
                default -> throw new InternalServiceException("Unsupported cloud provider " + cloudProvider);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServiceException(e.getMessage());
        }

    }


}
