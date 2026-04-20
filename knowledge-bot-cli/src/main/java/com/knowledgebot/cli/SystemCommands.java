package com.knowledgebot.cli;

import com.knowledgebot.ai.model.BotMode;
import com.knowledgebot.ai.model.ModeManager;
import com.knowledgebot.ai.model.ModelRouterService;
import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.ai.ingestion.WorkspaceWatcherService;
import com.knowledgebot.ai.notifications.NotificationEvent;
import com.knowledgebot.ai.notifications.NotificationService;
import com.knowledgebot.core.scanner.SemanticIndexingService;
import com.knowledgebot.core.security.CommandPermissionService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles system-level commands: status, mode switching, workspace, file watching,
 * notifications, and semantic analysis.
 * Extracted from the monolithic KnowledgeBotCommands.
 */
@ShellComponent
public class SystemCommands {

    private final ModeManager modeManager;
    private final ModelRouterService modelRouterService;
    private final CommandPermissionService permissionService;
    private final WorkspaceManager workspaceManager;
    private final WorkspaceWatcherService watcherService;
    private final NotificationService notificationService;
    private final SemanticIndexingService semanticIndexingService;

    public SystemCommands(ModeManager modeManager,
                          ModelRouterService modelRouterService,
                          CommandPermissionService permissionService,
                          WorkspaceManager workspaceManager,
                          WorkspaceWatcherService watcherService,
                          NotificationService notificationService,
                          SemanticIndexingService semanticIndexingService) {
        this.modeManager = modeManager;
        this.modelRouterService = modelRouterService;
        this.permissionService = permissionService;
        this.workspaceManager = workspaceManager;
        this.watcherService = watcherService;
        this.notificationService = notificationService;
        this.semanticIndexingService = semanticIndexingService;
    }

    @ShellMethod(key = "status", value = "Show current mode, model status, and live routing metrics")
    public String status() {
        String modeInfo = modeManager.getModeDescription();
        String modelInfo = "Current model: " + modelRouterService.getLastUsedModel();
        String metrics = modelRouterService.getMetricsReport();
        return "=== Knowledge Bot Status ===\n" + modeInfo + "\n" + modelInfo + "\n\n" + metrics;
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

    @ShellMethod(key = "safe-mode", value = "Toggle Safe Mode (Human-in-the-Loop permission checks)")
    public String toggleSafeMode(@ShellOption(defaultValue = "true") boolean enabled) {
        permissionService.setSafeMode(enabled);
        return "Safe Mode set to: " + enabled;
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

    @ShellMethod(key = "semantic-analyze", value = "Show semantic summary (signatures, call graph, imports) of a Java file")
    public String semanticAnalyze(@ShellOption String fileTarget) {
        Path targetPath = workspaceManager.resolve(fileTarget);
        if (!semanticIndexingService.supports(targetPath)) {
            return "[ERROR] Only .java files are supported for semantic analysis.";
        }
        try {
            String summary = semanticIndexingService.buildSemanticSummary(targetPath);
            var callGraph = semanticIndexingService.extractCallGraph(targetPath);
            var imports = semanticIndexingService.extractImportGraph(targetPath);
            return "=== Semantic Summary: " + targetPath.getFileName() + " ===\n\n"
                    + summary
                    + "\n--- Call Graph ---\n" + String.join("\n", callGraph)
                    + "\n--- Import Graph ---\n" + String.join("\n", imports);
        } catch (java.io.IOException e) {
            return "[ERROR] Failed to analyze file: " + e.getMessage();
        }
    }
}
