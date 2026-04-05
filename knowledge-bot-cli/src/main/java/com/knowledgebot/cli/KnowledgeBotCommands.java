package com.knowledgebot.cli;

import com.knowledgebot.core.scanner.SemanticIndexingService;
import com.knowledgebot.ai.generation.CodeGenerationService;
import com.knowledgebot.ai.migration.MigrationGeneratorService;
import com.knowledgebot.ai.modernize.DiffGenerationService;
import com.knowledgebot.ai.model.BotMode;
import com.knowledgebot.ai.model.ModeManager;
import com.knowledgebot.ai.model.ModelRouterService;
import com.knowledgebot.ai.model.PlanFileService;
import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.ai.pipeline.EmbeddingPipeline;
import com.knowledgebot.ai.prompt.PromptCompositionService;
import com.knowledgebot.ai.retrieval.RetrievalService;
import com.knowledgebot.ai.orchestration.OrchestratorService;
import com.knowledgebot.ai.ingestion.WorkspaceWatcherService;
import com.knowledgebot.ai.notifications.NotificationService;
import com.knowledgebot.ai.notifications.NotificationEvent;
import com.knowledgebot.ai.devops.DeploymentAgent;
import com.knowledgebot.ai.devops.GitHubApiService;
import com.knowledgebot.ai.review.PRReviewService;
import com.knowledgebot.core.performance.TokenBudgetService;
import com.knowledgebot.core.scanner.WorkspaceScannerService;
import com.knowledgebot.ai.planning.PlanningService;
import com.knowledgebot.core.security.CommandPermissionService;
import com.knowledgebot.core.security.OutputSanitizer;
import com.knowledgebot.core.security.PromptInjectionGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@ShellComponent
public class KnowledgeBotCommands {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBotCommands.class);

    private final WorkspaceScannerService scannerService;
    private final EmbeddingPipeline embeddingPipeline;
    private final RetrievalService retrievalService;
    private final PromptCompositionService promptCompositionService;
    private final ChatClient chatClient;

    // Phase 3 Extensions
    private final CodeGenerationService codeGenService;
    private final DiffGenerationService legacyDiffService;
    private final PRReviewService prReviewService;
    private final MigrationGeneratorService migrationService;

    // Phase 4 Guards & Monitors
    private final OutputSanitizer sanitizer;
    private final PromptInjectionGuard injectionGuard;
    private final TokenBudgetService tokenBudgetService;

    // Agentic Features
    private final CommandPermissionService permissionService;
    private final PlanningService planningService;

    // Upgrade 1: Dynamic Model Routing
    private final ModelRouterService modelRouterService;

    // 3-Mode System
    private final ModeManager modeManager;
    private final PlanFileService planFileService;

    // Upgrade 2: Concurrent Multi-Agent Orchestration
    private final OrchestratorService orchestratorService;

    // Upgrade 3: Continuous Ingestion & Multi-Format
    private final WorkspaceWatcherService watcherService;

    // Upgrade 5: DevOps & Deployment
    private final DeploymentAgent deploymentAgent;
    private final GitHubApiService githubApiService;

    // Upgrade 6: Async Notifications
    private final NotificationService notificationService;

    // Feature: Semantic Code Indexing
    private final SemanticIndexingService semanticIndexingService;

    private final WorkspaceManager workspaceManager;

    public KnowledgeBotCommands(WorkspaceScannerService scannerService,
                                EmbeddingPipeline embeddingPipeline,
                                RetrievalService retrievalService,
                                PromptCompositionService promptCompositionService,
                                ChatClient.Builder chatClientBuilder,
                                CodeGenerationService codeGenService,
                                DiffGenerationService legacyDiffService,
                                PRReviewService prReviewService,
                                MigrationGeneratorService migrationService,
                                OutputSanitizer sanitizer,
                                PromptInjectionGuard injectionGuard,
                                TokenBudgetService tokenBudgetService,
                                CommandPermissionService permissionService,
                                PlanningService planningService,
                                ModelRouterService modelRouterService,
                                ModeManager modeManager,
                                PlanFileService planFileService,
                                OrchestratorService orchestratorService,
                                WorkspaceWatcherService watcherService,
                                DeploymentAgent deploymentAgent,
                                GitHubApiService githubApiService,
                                NotificationService notificationService,
                                SemanticIndexingService semanticIndexingService,
                                WorkspaceManager workspaceManager) {
        this.scannerService = scannerService;
        this.embeddingPipeline = embeddingPipeline;
        this.retrievalService = retrievalService;
        this.promptCompositionService = promptCompositionService;
        this.chatClient = chatClientBuilder.build();
        this.codeGenService = codeGenService;
        this.legacyDiffService = legacyDiffService;
        this.prReviewService = prReviewService;
        this.migrationService = migrationService;
        this.sanitizer = sanitizer;
        this.injectionGuard = injectionGuard;
        this.tokenBudgetService = tokenBudgetService;
        this.permissionService = permissionService;
        this.planningService = planningService;
        this.modelRouterService = modelRouterService;
        this.modeManager = modeManager;
        this.planFileService = planFileService;
        this.orchestratorService = orchestratorService;
        this.watcherService = watcherService;
        this.deploymentAgent = deploymentAgent;
        this.githubApiService = githubApiService;
        this.notificationService = notificationService;
        this.semanticIndexingService = semanticIndexingService;
        this.workspaceManager = workspaceManager;
    }

    @ShellMethod(key = "scan", value = "Scan and index a project workspace")
    public String scan(@ShellOption(defaultValue = ".") String path) {
        Path rootPath = workspaceManager.resolve(path);
        List<Path> scannedFiles = scannerService.scanWorkspace(rootPath);
        embeddingPipeline.processAndEmbed(scannedFiles);
        return "Successfully scanned and indexed " + scannedFiles.size() + " files from " + rootPath;
    }

    @Cacheable("llmResponses")
    @ShellMethod(key = "chat", value = "Ask a question about the indexed workspace (ASK mode)")
    public String chat(@ShellOption String query) {
        if (modeManager.isReadOnly() || modeManager.getCurrentMode() == BotMode.ASK) {
            return handleAskQuery(query);
        }
        return handleAskQuery(query);
    }

    private String handleAskQuery(String query) {
        if (!injectionGuard.isSafe(query)) {
            return "[SECURITY BLOCK] Detected prompt injection attempt. Query rejected.";
        }

        tokenBudgetService.assertBudgetAvailable();

        List<Document> contextDocs = retrievalService.search(query, 5);
        Prompt prompt = promptCompositionService.buildPrompt(query, contextDocs);

        int activeBudget = modelRouterService.getActivePromptBudget();
        if (!tokenBudgetService.isWithinBudget(prompt.getContents(), activeBudget)) {
            return "[ERROR] Context exceeds model-aware budget (" + activeBudget + " tokens). Please narrow your search.";
        }

        ChatClient routedClient = modelRouterService.getClientForPrompt(query);
        String response = routedClient.prompt(prompt).call().content();

        tokenBudgetService.addTokens(tokenBudgetService.estimateTokens(prompt.getContents() + response));
        return sanitizer.sanitize(response);
    }

    @ShellMethod(key = "migrate-gen", value = "Generate Flyway Migration SQL out of JPA Entities")
    public String migrateGen(@ShellOption String fileTarget) {
        if (!modeManager.canModifyFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate migrations. Use: set-mode CODE";
        }
        if (!permissionService.requestPermission("Read/Analyze file to generate database migration: " + fileTarget)) {
            return "Action Denied by user.";
        }
        Path targetPath = workspaceManager.resolve(fileTarget);
        return sanitizer.sanitize(migrationService.generateMigration(targetPath));
    }

    @ShellMethod(key = "plan", value = "Generate a plan and write it to PLAN.md in the current directory")
    public String plan(@ShellOption String goal) {
        if (!injectionGuard.isSafe(goal)) return "[SECURITY BLOCK] Query rejected.";

        String planContent = planningService.generatePlan(goal);
        Path targetDir = workspaceManager.isWorkspaceAttached() ? workspaceManager.getActiveWorkspace() : Paths.get(".").toAbsolutePath();
        Path planFile = planFileService.writePlanToDisk(targetDir, planContent, goal);
        return "Plan generated and saved to: " + planFile.toAbsolutePath() + "\n\n" + planContent;
    }

    @ShellMethod(key = "set-mode", value = "Switch bot mode: PLAN, CODE, or ASK")
    public String setMode(@ShellOption BotMode mode) {
        try {
            modeManager.setMode(mode);
            return "Mode switched to: " + mode + " - " + modeManager.getModeDescription();
        } catch (IllegalStateException e) {
            return "[MODE ERROR] " + e.getMessage();
        }
    }

    @ShellMethod(key = "attach-workspace", value = "Attach the bot to a specific workspace directory (REQUIRED for CODE/PLAN modes)")
    public String attachWorkspace(@ShellOption String path) {
        try {
            Path wsPath = Paths.get(path);
            workspaceManager.attach(wsPath);
            return "Successfully attached to workspace: " + workspaceManager.getActiveWorkspace();
        } catch (IOException e) {
            return "[ERROR] Failed to attach workspace: " + e.getMessage();
        }
    }

    @ShellMethod(key = "status", value = "Show current mode, model status, and live routing metrics")
    public String status() {
        String modeInfo = modeManager.getModeDescription();
        String modelInfo = "Current model: " + modelRouterService.getLastUsedModel();
        String metrics = modelRouterService.getMetricsReport();
        return "=== Knowledge Bot Status ===\n" + modeInfo + "\n" + modelInfo + "\n\n" + metrics;
    }

    @ShellMethod(key = "safe-mode", value = "Toggle Safe Mode (Human-in-the-Loop permission checks)")
    public String toggleSafeMode(@ShellOption(defaultValue = "true") boolean enabled) {
        permissionService.setSafeMode(enabled);
        return "Safe Mode set to: " + enabled;
    }

    @ShellMethod(key = "modernize", value = "Modernize a legacy Java file to Java 25 idioms")
    public String modernize(@ShellOption String fileTarget) {
        if (!modeManager.canModifyFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to modernize files. Use: set-mode CODE";
        }
        if (!permissionService.requestPermission("Analyze and modernize file: " + fileTarget)) {
            return "Action Denied by user.";
        }
        try {
            Path targetPath = workspaceManager.resolve(fileTarget);
            return sanitizer.sanitize(legacyDiffService.modernize(targetPath));
        } catch (IOException e) {
            return "Failed to modernize: " + e.getMessage();
        }
    }

    @ShellMethod(key = "orchestrate", value = "Execute a plan with concurrent multi-agent parallel execution")
    public String orchestrate(@ShellOption String goal) {
        if (!injectionGuard.isSafe(goal)) return "[SECURITY BLOCK] Query rejected.";

        String planContent = planningService.generatePlan(goal);
        log.info("Generated plan, now executing with concurrent agents...");

        String result = orchestratorService.executePlanWithConcurrency(goal, planContent);
        return "=== Multi-Agent Execution Complete ===\n\n" + result;
    }

    @ShellMethod(key = "progress", value = "Check progress of current multi-agent execution")
    public String progress() {
        return orchestratorService.getProgressReport();
    }

    @ShellMethod(key = "plan-status", value = "Show live per-step status board of the active plan")
    public String planStatus() {
        var plan = orchestratorService.getActivePlan();
        if (plan == null) return "No active plan. Use 'plan <goal>' or 'orchestrate <goal>' first.";
        return plan.renderProgressBoard();
    }

    @ShellMethod(key = "watch", value = "Start continuous file watching for auto-indexing")
    public String watch(@ShellOption(defaultValue = ".") String path) {
        Path watchPath = workspaceManager.resolve(path);
        watcherService.startWatching(watchPath);
        return "Workspace watcher started on: " + watchPath + "\nFiles will be auto-indexed on save.";
    }

    @ShellMethod(key = "watch-stop", value = "Stop the workspace watcher")
    public String watchStop() {
        watcherService.stopWatching();
        return "Workspace watcher stopped. Files indexed: " + watcherService.getFilesIndexed();
    }

    @ShellMethod(key = "watch-status", value = "Check watcher status")
    public String watchStatus() {
        return "Watching: " + watcherService.isWatching() + " | Files indexed: " + watcherService.getFilesIndexed();
    }

    @ShellMethod(key = "generate", value = "Generate code based on instructions")
    public String generate(@ShellOption String prompt, @ShellOption(defaultValue = "") String fileTarget) {
        if (!modeManager.canModifyFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate code. Use: set-mode CODE";
        }
        if (!permissionService.requestPermission("Generate code based on prompt: " + prompt)) {
            return "Action Denied by user.";
        }
        if (!injectionGuard.isSafe(prompt)) return "[SECURITY BLOCK] Query rejected.";
        Path targetPath = fileTarget.isEmpty() ? null : workspaceManager.resolve(fileTarget);
        return sanitizer.sanitize(codeGenService.generate(prompt, targetPath));
    }

    @ShellMethod(key = "notify", value = "Send a test notification to all configured channels")
    public String notifyTest(@ShellOption(defaultValue = "Hello from Knowledge Bot!") String message) {
        notificationService.sendToAll(NotificationEvent.info("Test Notification", message));
        return "Test notification sent. Check your configured channels (Discord/Telegram/Webhook).";
    }

    @ShellMethod(key = "notify-history", value = "View notification history")
    public String notifyHistory() {
        var history = notificationService.getEventHistory();
        if (history.isEmpty()) return "No notifications sent yet.";
        StringBuilder sb = new StringBuilder("=== Notification History (" + history.size() + ") ===\n");
        for (var event : history) {
            sb.append(String.format("[%s] %s: %s\n", event.severity().toUpperCase(), event.title(), event.message()));
        }
        return sb.toString();
    }

    @ShellMethod(key = "deploy-docker", value = "Generate Docker Compose for a project")
    public String deployDocker(@ShellOption String projectName, @ShellOption(defaultValue = "app,database,cache") String services) {
        if (!modeManager.canCreateFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate deployment configs. Use: set-mode CODE";
        }
        List<String> serviceList = List.of(services.split(","));
        String compose = deploymentAgent.generateDockerCompose(projectName, serviceList);
        Path targetDir = workspaceManager.isWorkspaceAttached() ? workspaceManager.getActiveWorkspace() : Paths.get(".").toAbsolutePath();
        Path artifact = deploymentAgent.writeDeploymentArtifact(targetDir, "docker-compose.yml", compose);
        notificationService.notifyTaskComplete("Docker Compose Generated", projectName);
        return "Docker Compose generated: " + artifact.toAbsolutePath();
    }

    @ShellMethod(key = "deploy-terraform", value = "Generate Terraform config for cloud deployment")
    public String deployTerraform(@ShellOption String projectName, @ShellOption(defaultValue = "aws") String provider, @ShellOption(defaultValue = "us-east-1") String region) {
        if (!modeManager.canCreateFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate deployment configs. Use: set-mode CODE";
        }
        String tf = deploymentAgent.generateTerraformConfig(provider, projectName, region);
        Path targetDir = workspaceManager.isWorkspaceAttached() ? workspaceManager.getActiveWorkspace() : Paths.get(".").toAbsolutePath();
        Path artifact = deploymentAgent.writeDeploymentArtifact(targetDir, "main.tf", tf);
        return "Terraform config generated: " + artifact.toAbsolutePath();
    }

    @ShellMethod(key = "deploy-cicd", value = "Generate GitHub Actions CI/CD workflow")
    public String deployCicd(@ShellOption String projectName, @ShellOption(defaultValue = "test,build,deploy-staging,deploy-prod") String stages) {
        if (!modeManager.canCreateFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate deployment configs. Use: set-mode CODE";
        }
        List<String> stageList = List.of(stages.split(","));
        String workflow = deploymentAgent.generateGithubActions(projectName, stageList);
        Path targetDir = workspaceManager.isWorkspaceAttached() ? workspaceManager.getActiveWorkspace() : Paths.get(".").toAbsolutePath();
        Path dir = targetDir.resolve(".github/workflows");
        Path artifact = deploymentAgent.writeDeploymentArtifact(dir, "ci-cd.yml", workflow);
        return "GitHub Actions workflow generated: " + artifact.toAbsolutePath();
    }

    @ShellMethod(key = "deploy-k8s", value = "Generate Kubernetes manifests")
    public String deployK8s(@ShellOption String appName, @ShellOption(defaultValue = "2") int replicas, @ShellOption(defaultValue = "app:latest") String image) {
        if (!modeManager.canCreateFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate deployment configs. Use: set-mode CODE";
        }
        String manifests = deploymentAgent.generateKubernetesManifests(appName, replicas, image);
        Path targetDir = workspaceManager.isWorkspaceAttached() ? workspaceManager.getActiveWorkspace() : Paths.get(".").toAbsolutePath();
        Path artifact = deploymentAgent.writeDeploymentArtifact(targetDir, "k8s-manifests.yml", manifests);
        return "Kubernetes manifests generated: " + artifact.toAbsolutePath();
    }

    @ShellMethod(key = "deploy-full", value = "Generate complete deployment plan (Docker + Terraform + CI/CD + K8s)")
    public String deployFull(@ShellOption String projectName, @ShellOption(defaultValue = "aws") String provider, @ShellOption(defaultValue = "us-east-1") String region) {
        if (!modeManager.canCreateFiles()) {
            return "[MODE BLOCK] Switch to CODE mode to generate deployment configs. Use: set-mode CODE";
        }
        String plan = deploymentAgent.generateFullDeploymentPlan(projectName, provider, region,
                Map.of("services", List.of("app", "database", "cache"),
                       "ciStages", List.of("test", "build", "deploy"),
                       "image", projectName + ":latest",
                       "replicas", 2));
        Path targetDir = workspaceManager.isWorkspaceAttached() ? workspaceManager.getActiveWorkspace() : Paths.get(".").toAbsolutePath();
        Path artifact = deploymentAgent.writeDeploymentArtifact(targetDir, "DEPLOYMENT_PLAN.md", plan);
        notificationService.notifyDeploymentComplete(projectName, "full");
        return "Full deployment plan generated: " + artifact.toAbsolutePath();
    }

    @ShellMethod(key = "github-pr", value = "Create a GitHub Pull Request")
    public String githubPr(@ShellOption String owner, @ShellOption String repo, @ShellOption String title, @ShellOption String body, @ShellOption String headBranch, @ShellOption(defaultValue = "main") String baseBranch) {
        if (!modeManager.canModifyFiles()) {
            return "[MODE BLOCK] Switch to CODE mode for GitHub operations. Use: set-mode CODE";
        }
        return githubApiService.createPullRequest(owner, repo, title, body, headBranch, baseBranch);
    }

    @ShellMethod(key = "github-ci-status", value = "Check CI/CD status for a repository")
    public String githubCiStatus(@ShellOption String owner, @ShellOption String repo, @ShellOption(defaultValue = "main") String branch) {
        return githubApiService.getCIStatus(owner, repo, branch);
    }

    @ShellMethod(key = "github-token", value = "Set GitHub API authentication token")
    public String githubToken(@ShellOption String token) {
        githubApiService.setAuthToken(token);
        return "GitHub API token set successfully.";
    }

    @ShellMethod(key = "semantic-analyze", value = "Show semantic summary (signatures, call graph, imports) of a Java file")
    public String semanticAnalyze(@ShellOption String fileTarget) {
        Path targetPath = workspaceManager.resolve(fileTarget);
        if (!semanticIndexingService.supports(targetPath)) {
            return "[ERROR] Only .java files are supported for semantic analysis.";
        }
        try {
            String summary = semanticIndexingService.buildSemanticSummary(targetPath);
            List<String> callGraph = semanticIndexingService.extractCallGraph(targetPath);
            List<String> imports = semanticIndexingService.extractImportGraph(targetPath);
            return "=== Semantic Summary: " + targetPath.getFileName() + " ===\n\n"
                    + summary
                    + "\n--- Call Graph ---\n" + String.join("\n", callGraph)
                    + "\n--- Import Graph ---\n" + String.join("\n", imports);
        } catch (java.io.IOException e) {
            return "[ERROR] Failed to analyze file: " + e.getMessage();
        }
    }
}
