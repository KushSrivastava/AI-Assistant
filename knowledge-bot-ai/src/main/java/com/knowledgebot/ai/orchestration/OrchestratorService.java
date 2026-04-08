package com.knowledgebot.ai.orchestration;

// Explicitly importing intra-package classes to resolve IDE cache issues
import com.knowledgebot.ai.mcp.McpClientService;
import com.knowledgebot.ai.orchestration.DAGScheduler;
import com.knowledgebot.ai.orchestration.KnowledgeMerger;
import com.knowledgebot.ai.orchestration.DagTask;
import com.knowledgebot.ai.orchestration.TaskStatus;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import com.knowledgebot.ai.notifications.NotificationService;
import com.knowledgebot.ai.planning.PlanningService;
import com.knowledgebot.ai.planning.TrackedPlan;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates multi-agent concurrent plan execution using Project Loom Virtual Threads.
 *
 * Integrates {@link TrackedPlan} with {@link DAGScheduler} and natively binds
 * Model Context Protocol (MCP) tools to all executing agents.
 */
@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final DAGScheduler dagScheduler;
    private final KnowledgeMerger knowledgeMerger;
    private final ChatClient.Builder chatClientBuilder;
    private final NotificationService notificationService;
    private final McpClientService mcpClientService;

    /** The current active plan — updated on each executePlanWithConcurrency call. */
    private final AtomicReference<TrackedPlan> activePlan = new AtomicReference<>();

    public OrchestratorService(DAGScheduler dagScheduler,
                               KnowledgeMerger knowledgeMerger,
                               ChatClient.Builder chatClientBuilder,
                               NotificationService notificationService,
                               McpClientService mcpClientService) {
        this.dagScheduler = dagScheduler;
        this.knowledgeMerger = knowledgeMerger;
        this.chatClientBuilder = chatClientBuilder;
        this.notificationService = notificationService;
        this.mcpClientService = mcpClientService;
    }

    // -------------------------------------------------------------------------

    /**
     * Dynamically builds a ChatClient equipped with all currently connected MCP tools.
     * This is the magic of Spring AI 2.x!
     */
    private ChatClient buildMcpEnabledAgent() {
        ChatClient.Builder builder = chatClientBuilder.clone();

        List<McpSyncClient> activeServers = mcpClientService.getActiveConnections();

        if (!activeServers.isEmpty()) {
            // Spring AI 2.0 utility to adapt official MCP tools to Spring AI ToolCallbacks
            List<ToolCallback> mcpTools = SyncMcpToolCallbackProvider.syncToolCallbacks(activeServers);

            // Bind all discovered tools to the LLM natively
            builder.defaultToolCallbacks(mcpTools.toArray(new ToolCallback[0]));
        }

        return builder.build();
    }

    // -------------------------------------------------------------------------

    public String executePlanWithConcurrency(String goal, String markdownPlan) {
        log.info("Orchestrating plan with concurrent execution for goal: {}", goal);
        notificationService.notifyPlanGenerated(goal);

        List<String> steps = PlanningService.parseSteps(markdownPlan);
        TrackedPlan plan = new TrackedPlan(goal, markdownPlan, steps);
        activePlan.set(plan);

        List<DagTask> tasks = dagScheduler.buildGraphFromPlan(markdownPlan);
        log.info("Task graph built: {} tasks", tasks.size());

        // Build a fresh agent with the latest tools and pass it to the Loom Scheduler
        ChatClient agentClient = buildMcpEnabledAgent();
        Map<String, String> results = dagScheduler.executeAll(agentClient, 300);

        syncStatusesToPlan(plan, tasks, results);

        log.info("Execution complete.\n{}", plan.renderProgressBoard());

        String merged = knowledgeMerger.mergeResults(goal, results);
        notificationService.notifyTaskComplete(goal,
                "Completed " + plan.getCompletionPercent() + "% of plan");
        return merged;
    }

    public String executeParallelTasks(String goal,
                                       List<String> independentTasks,
                                       List<String> sequentialTasks) {
        log.info("Orchestrating parallel task execution for goal: {}", goal);

        List<String> allSteps = new java.util.ArrayList<>(independentTasks);
        allSteps.addAll(sequentialTasks);
        TrackedPlan plan = new TrackedPlan(goal, "", allSteps);
        activePlan.set(plan);

        List<DagTask> tasks = dagScheduler.buildGraphWithParallelTasks(independentTasks, sequentialTasks);

        // Build a fresh agent with the latest tools
        ChatClient agentClient = buildMcpEnabledAgent();
        Map<String, String> results = dagScheduler.executeAll(agentClient, 300);

        syncStatusesToPlan(plan, tasks, results);

        log.info("Parallel execution complete.\n{}", plan.renderProgressBoard());
        return knowledgeMerger.mergeResults(goal, results);
    }

    // -------------------------------------------------------------------------

    public String getProgressReport() {
        TrackedPlan plan = activePlan.get();
        if (plan != null) {
            return plan.renderProgressBoard();
        }
        return dagScheduler.getProgressReport();
    }

    public TrackedPlan getActivePlan() {
        return activePlan.get();
    }

    private void syncStatusesToPlan(TrackedPlan plan, List<DagTask> tasks, Map<String, String> results) {
        for (int i = 0; i < tasks.size() && i < plan.getSteps().size(); i++) {
            DagTask task = tasks.get(i);
            TrackedPlan.Step step = plan.getStep(i);
            TaskStatus dagStatus = task.status().get();
            String result = results.getOrDefault(task.id(), "");

            switch (dagStatus) {
                case COMPLETED  -> step.markCompleted(result);
                case FAILED     -> step.markFailed(result);
                case BLOCKED    -> step.markBlocked();
                default         -> { /* leave as NOT_STARTED */ }
            }
        }
    }
}