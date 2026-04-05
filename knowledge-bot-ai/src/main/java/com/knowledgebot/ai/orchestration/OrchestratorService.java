package com.knowledgebot.ai.orchestration;

import com.knowledgebot.ai.notifications.NotificationService;
import com.knowledgebot.ai.planning.PlanningService;
import com.knowledgebot.ai.planning.TrackedPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates multi-agent concurrent plan execution.
 *
 * Integrates {@link TrackedPlan} (per-step lifecycle: NOT_STARTED → IN_PROGRESS →
 * COMPLETED | FAILED | BLOCKED) with {@link DAGScheduler} so every task's status
 * is reflected in both the DAG and the plan's progress board.
 *
 * Stuck-state detection is handled inside {@link DAGScheduler}.
 */
@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final DAGScheduler dagScheduler;
    private final KnowledgeMerger knowledgeMerger;
    private final ChatClient chatClient;
    private final NotificationService notificationService;

    /** The current active plan — updated on each executePlanWithConcurrency call. */
    private final AtomicReference<TrackedPlan> activePlan = new AtomicReference<>();

    public OrchestratorService(DAGScheduler dagScheduler,
                               KnowledgeMerger knowledgeMerger,
                               ChatClient.Builder chatClientBuilder,
                               NotificationService notificationService) {
        this.dagScheduler = dagScheduler;
        this.knowledgeMerger = knowledgeMerger;
        this.chatClient = chatClientBuilder.build();
        this.notificationService = notificationService;
    }

    // -------------------------------------------------------------------------

    /**
     * Generates a tracked plan, builds the DAG, executes all tasks concurrently,
     * and synchronises per-step statuses back to the TrackedPlan for live progress.
     */
    public String executePlanWithConcurrency(String goal, String markdownPlan) {
        log.info("Orchestrating plan with concurrent execution for goal: {}", goal);
        notificationService.notifyPlanGenerated(goal);

        // Build TrackedPlan from the markdown
        List<String> steps = PlanningService.parseSteps(markdownPlan);
        TrackedPlan plan = new TrackedPlan(goal, markdownPlan, steps);
        activePlan.set(plan);

        // Build DAG and mark steps IN_PROGRESS as they start
        List<DagTask> tasks = dagScheduler.buildGraphFromPlan(markdownPlan);
        log.info("Task graph built: {} tasks", tasks.size());

        // Execute — DAGScheduler handles retries and stuck-state detection internally
        Map<String, String> results = dagScheduler.executeAll(chatClient, 300);

        // Synchronise final statuses from DAG → TrackedPlan
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
        log.info("DAG built: {} independent + {} sequential tasks",
                independentTasks.size(), sequentialTasks.size());

        Map<String, String> results = dagScheduler.executeAll(chatClient, 300);
        syncStatusesToPlan(plan, tasks, results);

        log.info("Parallel execution complete.\n{}", plan.renderProgressBoard());
        return knowledgeMerger.mergeResults(goal, results);
    }

    // -------------------------------------------------------------------------

    /**
     * Returns the live progress board of the currently active plan.
     * Falls back to the DAGScheduler's progress report if no plan is active.
     */
    public String getProgressReport() {
        TrackedPlan plan = activePlan.get();
        if (plan != null) {
            return plan.renderProgressBoard();
        }
        return dagScheduler.getProgressReport();
    }

    /** Returns the active TrackedPlan, or null if none has been started. */
    public TrackedPlan getActivePlan() {
        return activePlan.get();
    }

    // -------------------------------------------------------------------------

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
