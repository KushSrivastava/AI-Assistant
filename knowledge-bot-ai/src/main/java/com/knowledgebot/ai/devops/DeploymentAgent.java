package com.knowledgebot.ai.devops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

@Service
public class DeploymentAgent {

    private static final Logger log = LoggerFactory.getLogger(DeploymentAgent.class);

    private final ChatClient chatClient;

    public DeploymentAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String generateDockerCompose(String projectName, List<String> services) {
        String prompt = """
            Generate a production-ready docker-compose.yml for a project named "%s".
            Services required: %s
            Include proper networking, volume mounts, health checks, and restart policies.
            Output only the YAML content, no explanations.
            """.formatted(projectName, String.join(", ", services));

        return chatClient.prompt().user(prompt).call().content();
    }

    public String generateTerraformConfig(String provider, String appName, String region) {
        String prompt = """
            Generate Terraform configuration for deploying "%s" on %s in region "%s".
            Include:
            - Provider configuration
            - VPC and networking
            - Compute resources (ECS/EKS/Cloud Run)
            - Database (RDS/Cloud SQL)
            - Output variables
            Output only the HCL content, no explanations.
            """.formatted(appName, provider, region);

        return chatClient.prompt().user(prompt).call().content();
    }

    public String generateGithubActions(String projectName, List<String> stages) {
        String prompt = """
            Generate a GitHub Actions workflow for "%s".
            CI/CD stages: %s
            Include:
            - Trigger on push to main and PR
            - Build and test
            - Security scanning
            - Docker image build and push
            - Deploy to staging/production
            Output only the YAML content, no explanations.
            """.formatted(projectName, String.join(", ", stages));

        return chatClient.prompt().user(prompt).call().content();
    }

    public String generateKubernetesManifests(String appName, int replicas, String image) {
        String prompt = """
            Generate Kubernetes manifests for deploying "%s".
            - Image: %s
            - Replicas: %d
            Include:
            - Deployment
            - Service (ClusterIP + Ingress)
            - ConfigMap
            - Secret (placeholder)
            - HorizontalPodAutoscaler
            Output all YAML in a single response with --- separators.
            """.formatted(appName, image, replicas);

        return chatClient.prompt().user(prompt).call().content();
    }

    public Path writeDeploymentArtifact(Path targetDir, String filename, String content) {
        Path artifact = targetDir.resolve(filename);
        try {
            Path parent = artifact.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(artifact, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Deployment artifact written to: {}", artifact);
            return artifact;
        } catch (IOException e) {
            log.error("Failed to write deployment artifact: {}", e.getMessage());
            throw new RuntimeException("Failed to write deployment artifact", e);
        }
    }

    public String generateFullDeploymentPlan(String projectName, String provider, String region, Map<String, Object> config) {
        StringBuilder plan = new StringBuilder();
        plan.append("# Deployment Plan: ").append(projectName).append("\n\n");

        List<String> services = (List<String>) config.getOrDefault("services", List.of("app", "database", "cache"));
        String dockerCompose = generateDockerCompose(projectName, services);
        plan.append("## Docker Compose\n\n```yaml\n").append(dockerCompose).append("\n```\n\n");

        String terraform = generateTerraformConfig(provider, projectName, region);
        plan.append("## Terraform Infrastructure\n\n```hcl\n").append(terraform).append("\n```\n\n");

        List<String> stages = (List<String>) config.getOrDefault("ciStages", List.of("test", "build", "deploy-staging", "deploy-prod"));
        String githubActions = generateGithubActions(projectName, stages);
        plan.append("## GitHub Actions CI/CD\n\n```yaml\n").append(githubActions).append("\n```\n\n");

        String image = (String) config.getOrDefault("image", projectName + ":latest");
        int replicas = (int) config.getOrDefault("replicas", 2);
        String k8s = generateKubernetesManifests(projectName, replicas, image);
        plan.append("## Kubernetes Manifests\n\n```yaml\n").append(k8s).append("\n```\n\n");

        return plan.toString();
    }
}
