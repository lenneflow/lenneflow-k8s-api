package de.lenneflow.lenneflowterraformserver.util;

import de.lenneflow.lenneflowterraformserver.dto.ClusterDTO;
import de.lenneflow.lenneflowterraformserver.exception.InternalServiceException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class Util {

    public static String getTerraformBaseDir(){
        return  System.getProperty("user.home")+ File.separator + "Terraform";
    }

    public static String getTerraformClusterDir(String clusterName, String region){
        return  getTerraformBaseDir() + File.separator + clusterName + "-" + region;
    }

    public static String getTerraformSubDir(String clusterDir, String createFolderPath) {
        String suffix = createFolderPath.replace("/", File.separator). replace("\\", File.separator);
        if(!suffix.startsWith(File.separator)){
            suffix = File.separator + suffix;
        }
        return clusterDir + suffix;
    }


    public static File getClusterTerraformDirectory(String clusterName, String region){
        String terraformBaseDir = getTerraformBaseDir();
        String clusterDirPath = getTerraformClusterDir(clusterName, region);
        File terraformDirectory = new File(terraformBaseDir);
        if(!terraformDirectory.exists() && !terraformDirectory.mkdirs()){
            throw new InternalServiceException("Unable to create directory " + terraformBaseDir);
        }
        return new File(clusterDirPath);
    }


    public static String gitCloneOrUpdate(String repositoryUrl, String branch, String clusterName, String region){
        try {
            String clusterDirPath = getTerraformClusterDir(clusterName, region);
            if(!new File(clusterDirPath).exists()){
                File destinationDir = getClusterTerraformDirectory(clusterName, region);
                Git.cloneRepository().setURI(repositoryUrl).setBranch(branch).setDirectory(destinationDir).call();
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
                System.out.println(line);
            }
            // Wait for the process to complete
            int exitCode = process.waitFor();
            System.out.println("\nCommand executed with exit code: " + exitCode);
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
            System.out.println(".tfvars file created successfully at " + filePath);
        } catch (IOException e) {
            System.err.println("Error writing .tfvars file: " + e.getMessage());
        }
    }
}
