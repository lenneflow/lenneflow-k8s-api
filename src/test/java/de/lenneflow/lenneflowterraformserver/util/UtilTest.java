package de.lenneflow.lenneflowterraformserver.util;

import de.lenneflow.lenneflowterraformserver.dto.ClusterDTO;
import de.lenneflow.lenneflowterraformserver.enums.CloudProvider;
import de.lenneflow.lenneflowterraformserver.exception.InternalServiceException;
import de.lenneflow.lenneflowterraformserver.model.Credential;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UtilTest {

    private static final String BASE_DIR = System.getProperty("user.home") + File.separator + "Lenneflow";

    @BeforeEach
    void setUp() {
        // Ensure the base directory is clean before each test
        File baseDir = new File(BASE_DIR);
        if (baseDir.exists()) {
            FileUtils.deleteQuietly(baseDir);
        }
    }

    @Test
    void getBaseDirCreatesDirectoryIfNotExists() {
        String baseDir = Util.getBaseDir();
        assertTrue(new File(baseDir).exists());
    }

    @Test
    void getClusterDirCreatesDirectoryIfNotExists() {
        String clusterDir = Util.getClusterDir(CloudProvider.AWS, "test-cluster", "us-west-1");
        assertTrue(new File(clusterDir).exists());
    }

    @Test
    void runCmdCommandReturnsExitCode() throws IOException, InterruptedException {
        int exitCode = Util.runCmdCommand("echo Hello");
        assertEquals(0, exitCode);
    }

    @Test
    void runCmdCommandAndGetOutputReturnsOutput() throws IOException, InterruptedException {
        String output = Util.runCmdCommandAndGetOutput("echo Hello");
        assertEquals("Hello\n", output);
    }

    @Test
    void createTfvarsFileCreatesFileWithVariables() {
        Map<String, String> variables = new HashMap<>();
        variables.put("key", "value");
        String dirPath = Util.getBaseDir();
        Util.createTfvarsFile(dirPath, variables);
        File tfvarsFile = new File(dirPath + File.separator + "terraform.tfvars");
        assertTrue(tfvarsFile.exists());
    }

    @Test
    void createTfvarsFileWritesCorrectContent() throws IOException {
        Map<String, String> variables = new HashMap<>();
        variables.put("key", "value");
        String dirPath = Util.getBaseDir();
        Util.createTfvarsFile(dirPath, variables);
        File tfvarsFile = new File(dirPath + File.separator + "terraform.tfvars");
        String content = FileUtils.readFileToString(tfvarsFile, "UTF-8");
        assertTrue(content.contains("key = \"value\""));
    }

    @Test
    void setCredentialsEnvironmentVariablesThrowsExceptionForUnsupportedProvider() {
        Credential credential = new Credential();
        credential.setAccessKey("accessKey");
        credential.setSecretKey("secretKey");

        assertThrows(InternalServiceException.class, () -> Util.setCredentialsEnvironmentVariables(CloudProvider.GOOGLE, credential));
    }



    @Test
    void createTfvarsVariablesMapCreatesCorrectMap() {
        ClusterDTO clusterDTO = new ClusterDTO();
        clusterDTO.setClusterName("test-cluster");
        clusterDTO.setRegion("us-west-1");
        clusterDTO.setKubernetesVersion("1.18");
        clusterDTO.setDesiredNodeCount(3);
        clusterDTO.setMinimumNodeCount(1);
        clusterDTO.setMaximumNodeCount(5);
        clusterDTO.setInstanceType("t2.medium");
        clusterDTO.setAmiType("AL2_x86_64");
        clusterDTO.setAccessKey("accessKey");
        clusterDTO.setSecretKey("secretKey");

        Map<String, String> variables = Util.createTfvarsVariablesMap(clusterDTO);
        assertEquals("test-cluster", variables.get("cluster_name"));
        assertEquals("us-west-1", variables.get("region"));
        assertEquals("1.18", variables.get("cluster_version"));
        assertEquals("3", variables.get("node_group_desired_size"));
        assertEquals("1", variables.get("node_group_min_size"));
        assertEquals("5", variables.get("node_group_max_size"));
        assertEquals("t2.medium", variables.get("instance_type"));
        assertEquals("AL2_x86_64", variables.get("ami_type"));
        assertEquals("accessKey", variables.get("aws_access_key"));
        assertEquals("secretKey", variables.get("aws_secret_key"));
    }
}