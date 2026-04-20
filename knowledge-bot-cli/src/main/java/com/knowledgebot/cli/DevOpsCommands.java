package com.knowledgebot.cli;

import com.knowledgebot.ai.devops.DeploymentAgent;
import com.knowledgebot.ai.model.ModeManager;
import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.ai.notifications.NotificationService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Handles deployment-related commands (Docker, Terraform, CI/CD, Kubernetes).
 * Extracted from the monolithic KnowledgeBotCommands.
 */
@ShellComponent
public class DevOpsCommands {

    private final DeploymentAgent deploymentAgent;
    private final ModeManager modeManager;
    private final WorkspaceManager workspaceManager;
    private final NotificationService notificationService;

    public DevOpsCommands(DeploymentAgent deploymentAgent,
                          ModeManager modeManager,
                          WorkspaceManager workspaceManager,
                          NotificationService notificationService) {
        this.deploymentAgent = deploymentAgent;
        this.modeManager = modeManager;
        this.workspaceManager = workspaceManager;
        this.notificationService = notificationService;
    }

    @ShellMethod(key = "deploy-docker", value = "Generate Docker Compose for a project")
    public String deployDocker(@ShellOption String projectName,
                               @ShellOption(defaultValue = "app,database,cache") String services) {
        if (!modeManager.canCreateFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate deployment configs. Use: set-mode CODE";
        }
        List<String> serviceList = List.of(services.split(","));
        String compose = deploymentAgent.generateDockerCompose(projectName, serviceList);
        Path targetDir = resolveTargetDir();
        Path artifact = deploymentAgent.writeDeploymentArtifact(targetDir, "docker-compose.yml", compose);
        notificationService.notifyTaskComplete("Docker Compose Generated", projectName);
        return "Docker Compose generated: " + artifact.toAbsolutePath();
    }

    @ShellMethod(key = "deploy-terraform", value = "Generate Terraform config for cloud deployment")
    public String deployTerraform(@ShellOption String projectName,
                                  @ShellOption(defaultValue = "aws") String provider,
                                  @ShellOption(defaultValue = "us-east-1") String region) {
        if (!modeManager.canCreateFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate deployment configs. Use: set-mode CODE";
        }
        String tf = deploymentAgent.generateTerraformConfig(provider, projectName, region);
        Path artifact = deploymentAgent.writeDeploymentArtifact(resolveTargetDir(), "main.tf", tf);
        return "Terraform config generated: " + artifact.toAbsolutePath();
    }

    @ShellMethod(key = "deploy-cicd", value = "Generate GitHub Actions CI/CD workflow")
    public String deployCicd(@ShellOption String projectName,
                             @ShellOption(defaultValue = "test,build,deploy-staging,deploy-prod") String stages) {
        if (!modeManager.canCreateFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate deployment configs. Use: set-mode CODE";
        }
        List<String> stageList = List.of(stages.split(","));
        String workflow = deploymentAgent.generateGithubActions(projectName, stageList);
        Path dir = resolveTargetDir().resolve(".github/workflows");
        Path artifact = deploymentAgent.writeDeploymentArtifact(dir, "ci-cd.yml", workflow);
        return "GitHub Actions workflow generated: " + artifact.toAbsolutePath();
    }

    @ShellMethod(key = "deploy-k8s", value = "Generate Kubernetes manifests")
    public String deployK8s(@ShellOption String appName,
                            @ShellOption(defaultValue = "2") int replicas,
                            @ShellOption(defaultValue = "app:latest") String image) {
        if (!modeManager.canCreateFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate deployment configs. Use: set-mode CODE";
        }
        String manifests = deploymentAgent.generateKubernetesManifests(appName, replicas, image);
        Path artifact = deploymentAgent.writeDeploymentArtifact(resolveTargetDir(), "k8s-manifests.yml", manifests);
        return "Kubernetes manifests generated: " + artifact.toAbsolutePath();
    }

    @ShellMethod(key = "deploy-full", value = "Generate complete deployment plan (Docker + Terraform + CI/CD + K8s)")
    public String deployFull(@ShellOption String projectName,
                             @ShellOption(defaultValue = "aws") String provider,
                             @ShellOption(defaultValue = "us-east-1") String region) {
        if (!modeManager.canCreateFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate deployment configs. Use: set-mode CODE";
        }
        String plan = deploymentAgent.generateFullDeploymentPlan(projectName, provider, region,
                Map.of("services", List.of("app", "database", "cache"),
                       "ciStages", List.of("test", "build", "deploy"),
                       "image", projectName + ":latest",
                       "replicas", 2));
        Path artifact = deploymentAgent.writeDeploymentArtifact(resolveTargetDir(), "DEPLOYMENT_PLAN.md", plan);
        notificationService.notifyDeploymentComplete(projectName, "full");
        return "Full deployment plan generated: " + artifact.toAbsolutePath();
    }

    private Path resolveTargetDir() {
        return workspaceManager.isWorkspaceAttached()
                ? workspaceManager.getActiveWorkspace()
                : Paths.get(".").toAbsolutePath();
    }
}
