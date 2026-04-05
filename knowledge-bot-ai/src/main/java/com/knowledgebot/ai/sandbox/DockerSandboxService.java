package com.knowledgebot.ai.sandbox;

import com.knowledgebot.core.scanner.EnvironmentSensingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Logic-adapted from the user's recommendations: Implements Sandbox Execution.
 * Runs AI-generated tests in an isolated Docker container with Read-Only mounts.
 */
@Service
public class DockerSandboxService {
    private static final Logger log = LoggerFactory.getLogger(DockerSandboxService.class);
    private static final String DEFAULT_IMAGE = "maven:3.9.9-eclipse-temurin-21"; // Update to 25 once available in registry
    
    private final EnvironmentSensingService environmentSensing;

    public DockerSandboxService(EnvironmentSensingService environmentSensing) {
        this.environmentSensing = environmentSensing;
    }

    /**
     * Executes a command inside a Docker sandbox.
     * @param workDir The host directory to mount (Read-Only)
     * @param command The command to execute (e.g., "mvn test")
     * @return Execution result including logs.
     */
    public SandboxResult execute(Path workDir, String command) {
        if (!environmentSensing.getSensedTools().containsKey("docker")) {
            log.error("Docker not found in environment. Cannot execute in sandbox.");
            return new SandboxResult(false, "ERROR: Docker not installed. Sandbox execution blocked.");
        }

        log.info("Starting Sandbox Execution for command: {}", command);
        
        // Build the docker command
        // --rm: remove container after exit
        // -v: mount workDir as Read-Only
        // -w: set working directory in container
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "run", "--rm",
            "-v", workDir.toAbsolutePath() + ":/app:ro",
            "-w", "/app",
            DEFAULT_IMAGE,
            "sh", "-c", command
        );

        try {
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            boolean success = (exitCode == 0);
            
            log.info("Sandbox execution finished with exit code: {}", exitCode);
            return new SandboxResult(success, output);

        } catch (Exception e) {
            log.error("Sandbox execution failed: {}", e.getMessage());
            return new SandboxResult(false, "Exception during sandbox execution: " + e.getMessage());
        }
    }

    public record SandboxResult(boolean success, String logs) {}
}
