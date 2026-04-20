package com.knowledgebot.cli;

import com.knowledgebot.ai.model.BotMode;
import com.knowledgebot.ai.model.ModeManager;
import com.knowledgebot.ai.model.PlanFileService;
import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.ai.orchestration.OrchestratorService;
import com.knowledgebot.ai.planning.PlanningService;
import com.knowledgebot.core.security.PromptInjectionGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles planning and orchestration commands.
 * Extracted from the monolithic KnowledgeBotCommands.
 */
@ShellComponent
public class PlanCommands {

    private static final Logger log = LoggerFactory.getLogger(PlanCommands.class);

    private final PlanningService planningService;
    private final PlanFileService planFileService;
    private final OrchestratorService orchestratorService;
    private final WorkspaceManager workspaceManager;
    private final PromptInjectionGuard injectionGuard;
    private final ModeManager modeManager;

    public PlanCommands(PlanningService planningService,
                        PlanFileService planFileService,
                        OrchestratorService orchestratorService,
                        WorkspaceManager workspaceManager,
                        PromptInjectionGuard injectionGuard,
                        ModeManager modeManager) {
        this.planningService = planningService;
        this.planFileService = planFileService;
        this.orchestratorService = orchestratorService;
        this.workspaceManager = workspaceManager;
        this.injectionGuard = injectionGuard;
        this.modeManager = modeManager;
    }

    @ShellMethod(key = "plan", value = "Generate a plan and write it to PLAN.md in the current directory")
    public String plan(@ShellOption String goal) {
        if (!injectionGuard.isSafe(goal)) return "[SECURITY BLOCK] Query rejected.";

        String planContent = planningService.generatePlan(goal);
        Path targetDir = workspaceManager.isWorkspaceAttached()
                ? workspaceManager.getActiveWorkspace()
                : Paths.get(".").toAbsolutePath();
        Path planFile = planFileService.writePlanToDisk(targetDir, planContent, goal);
        return "Plan generated and saved to: " + planFile.toAbsolutePath() + "\n\n" + planContent;
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
}
