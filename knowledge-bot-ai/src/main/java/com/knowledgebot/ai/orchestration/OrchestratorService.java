package com.knowledgebot.ai.orchestration;

import com.knowledgebot.ai.mcp.McpClientService;
import com.knowledgebot.ai.notifications.NotificationService;
import com.knowledgebot.ai.orchestration.events.OrchestrationEvent.PlanCompletedEvent;
import com.knowledgebot.ai.planning.PlanningService;
import com.knowledgebot.ai.planning.TrackedPlan;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final DAGScheduler dagScheduler;
    private final KnowledgeMerger knowledgeMerger;
    private final ChatClient.Builder chatClientBuilder;
    private final NotificationService notificationService;
    private final McpClientService mcpClientService;

    private final AtomicReference<TrackedPlan> activePlan = new AtomicReference<>();

    // Tracks event-driven responses
    private final Map<String, CompletableFuture<Map<String, String>>> pendingPlans = new ConcurrentHashMap<>();

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

    private ChatClient buildMcpEnabledAgent() {
        ChatClient.Builder builder = chatClientBuilder.clone();
        List<McpSyncClient> activeServers = mcpClientService.getActiveConnections();

        if (!activeServers.isEmpty()) {
            List<ToolCallback> mcpTools = SyncMcpToolCallbackProvider.syncToolCallbacks(activeServers);
            builder.defaultToolCallbacks(mcpTools.toArray(new ToolCallback[0]));
        }
        return builder.build();
    }

    public String executePlanWithConcurrency(String goal, String markdownPlan) {
        log.info("Orchestrating event-driven plan for goal: {}", goal);
        notificationService.notifyPlanGenerated(goal);

        String planId = UUID.randomUUID().toString();
        List<String> steps = PlanningService.parseSteps(markdownPlan);
        TrackedPlan plan = new TrackedPlan(goal, markdownPlan, steps);
        activePlan.set(plan);

        List<DagTask> tasks = dagScheduler.buildGraphFromPlan(markdownPlan);

        CompletableFuture<Map<String, String>> futureResult = new CompletableFuture<>();
        pendingPlans.put(planId, futureResult);

        // Start non-blocking execution via event publisher
        dagScheduler.startPlanExecution(planId, tasks);

        try {
            // Main thread waits for the final PlanCompletedEvent
            Map<String, String> results = futureResult.get(300, TimeUnit.SECONDS);
            syncStatusesToPlan(plan, tasks, results);

            log.info("Execution complete.\n{}", plan.renderProgressBoard());

            String merged = knowledgeMerger.mergeResults(goal, results);
            notificationService.notifyTaskComplete(goal, "Completed " + plan.getCompletionPercent() + "% of plan");
            return merged;

        } catch (Exception e) {
            log.error("Plan execution failed or timed out", e);
            return "Execution failed: " + e.getMessage();
        } finally {
            pendingPlans.remove(planId);
        }
    }

    public String executeParallelTasks(String goal, List<String> independentTasks, List<String> sequentialTasks) {
        log.info("Orchestrating parallel event-driven tasks for goal: {}", goal);

        String planId = UUID.randomUUID().toString();
        List<String> allSteps = new ArrayList<>(independentTasks);
        allSteps.addAll(sequentialTasks);

        TrackedPlan plan = new TrackedPlan(goal, "", allSteps);
        activePlan.set(plan);

        List<DagTask> tasks = dagScheduler.buildGraphWithParallelTasks(independentTasks, sequentialTasks);

        CompletableFuture<Map<String, String>> futureResult = new CompletableFuture<>();
        pendingPlans.put(planId, futureResult);

        dagScheduler.startPlanExecution(planId, tasks);

        try {
            Map<String, String> results = futureResult.get(300, TimeUnit.SECONDS);
            syncStatusesToPlan(plan, tasks, results);
            log.info("Parallel execution complete.\n{}", plan.renderProgressBoard());
            return knowledgeMerger.mergeResults(goal, results);
        } catch (Exception e) {
            log.error("Parallel execution failed", e);
            return "Parallel execution failed: " + e.getMessage();
        } finally {
            pendingPlans.remove(planId);
        }
    }

    @EventListener
    public void onPlanCompleted(PlanCompletedEvent event) {
        CompletableFuture<Map<String, String>> future = pendingPlans.get(event.planId());
        if (future != null) {
            future.complete(event.finalResults());
        }
    }

    public String getProgressReport() {
        TrackedPlan plan = activePlan.get();
        if (plan != null) {
            return plan.renderProgressBoard();
        }
        return "No active plan running.";
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