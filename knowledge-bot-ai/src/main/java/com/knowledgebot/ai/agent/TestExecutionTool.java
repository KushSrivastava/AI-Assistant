package com.knowledgebot.ai.agent;

import com.knowledgebot.ai.sandbox.DockerSandboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.nio.file.Paths;
import java.util.function.Function;

/**
 * Logic-adapted from OpenManus: Implements autonomous tool use for verification.
 * Runs tests and returns results to the AI for reflection.
 */
@Configuration
public class TestExecutionTool {
    private static final Logger log = LoggerFactory.getLogger(TestExecutionTool.class);
    private final DockerSandboxService sandboxService;

    public TestExecutionTool(DockerSandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    public record TestRequest(String testCommand, String projectPath) {}
    public record TestResponse(boolean success, String output) {}

    @Bean
    @Description("Run a test command (e.g., './mvnw test') inside a secure Docker sandbox.")
    public Function<TestRequest, TestResponse> runTests() {
        return request -> {
            String command = request.testCommand();
            if (command == null || command.isEmpty()) {
                command = "mvn test"; // Default
            }

            String projectPath = request.projectPath();
            if (projectPath == null || projectPath.isEmpty()) {
                projectPath = "."; // Assume relative to current
            }

            log.info("Executing autonomous test in SANDBOX: {}", command);
            
            DockerSandboxService.SandboxResult result = sandboxService.execute(Paths.get(projectPath), command);
            
            return new TestResponse(result.success(), result.logs());
        };
    }
}
