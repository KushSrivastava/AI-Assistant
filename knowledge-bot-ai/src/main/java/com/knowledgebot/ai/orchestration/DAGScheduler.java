package com.knowledgebot.ai.orchestration;

import com.knowledgebot.ai.orchestration.events.OrchestrationEvent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DAGScheduler {

    private static final Logger log = LoggerFactory.getLogger(DAGScheduler.class);
    private static final Pattern TASK_LINE = Pattern.compile("^\\d+\\.\\s*\\[.*?]\\s*(.+)$", Pattern.MULTILINE);

    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, PlanExecutionState> activePlans = new ConcurrentHashMap<>();

    public DAGScheduler(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    // --- Graph Building Methods (Restored) ---

    public List<DagTask> buildGraphFromPlan(String markdownPlan) {
        List<DagTask> taskGraph = new ArrayList<>();
        Matcher matcher = TASK_LINE.matcher(markdownPlan);
        int index = 0;
        while (matcher.find()) {
            String description = matcher.group(1).trim();
            String taskId = "task-" + index;
            List<String> deps = index > 0 ? List.of("task-" + (index - 1)) : List.of();
            taskGraph.add(new DagTask(taskId, description, deps));
            index++;
        }
        log.info("Built DAG with {} tasks from plan", taskGraph.size());
        return taskGraph;
    }

    public List<DagTask> buildGraphWithParallelTasks(List<String> independentTasks, List<String> sequentialTasks) {
        List<DagTask> taskGraph = new ArrayList<>();
        int index = 0;

        for (String task : independentTasks) {
            taskGraph.add(new DagTask("indep-" + index, task, List.of()));
            index++;
        }

        String lastIndependent = index > 0 ? "indep-" + (index - 1) : null;
        for (String task : sequentialTasks) {
            List<String> deps = new ArrayList<>();
            if (lastIndependent != null) deps.add(lastIndependent);
            if (index > 0 && !taskGraph.isEmpty()) {
                String prevSequential = taskGraph.get(taskGraph.size() - 1).id();
                if (!deps.contains(prevSequential)) deps.add(prevSequential);
            }
            taskGraph.add(new DagTask("seq-" + index, task, deps));
            index++;
        }

        log.info("Built DAG with {} tasks ({} independent, {} sequential)", taskGraph.size(), independentTasks.size(), sequentialTasks.size());
        return taskGraph;
    }

    // --- Event Driven Execution Logic ---

    public void startPlanExecution(String planId, List<DagTask> taskGraph) {
        log.info("Starting execution for plan {} with {} tasks", planId, taskGraph.size());

        PlanExecutionState state = new PlanExecutionState(taskGraph);
        activePlans.put(planId, state);

        taskGraph.stream()
                .filter(task -> task.dependencies().isEmpty())
                .forEach(task -> {
                    task.status().set(TaskStatus.READY);
                    eventPublisher.publishEvent(new TaskReadyEvent(planId, task));
                });
    }

    @EventListener
    public void onTaskStarted(TaskStartedEvent event) {
        PlanExecutionState state = activePlans.get(event.planId());
        if (state != null) {
            state.getTask(event.taskId()).status().set(TaskStatus.EXECUTING);
        }
    }

    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        PlanExecutionState state = activePlans.get(event.planId());
        if (state == null) return;

        DagTask task = state.getTask(event.taskId());
        task.status().set(TaskStatus.COMPLETED);
        task.result().set(event.result());
        state.addResult(event.taskId(), event.result());

        checkDownstreamDependencies(event.planId(), state);
        checkPlanCompletion(event.planId(), state);
    }

    @EventListener
    public void onTaskFailed(TaskFailedEvent event) {
        PlanExecutionState state = activePlans.get(event.planId());
        if (state == null) return;

        DagTask task = state.getTask(event.taskId());
        task.status().set(TaskStatus.FAILED);
        task.result().set(event.error());

        state.getAllTasks().forEach(t -> {
            if (t.hasFailedDependencies(state.getAllTasks())) {
                t.status().set(TaskStatus.BLOCKED);
            }
        });

        checkPlanCompletion(event.planId(), state);
    }

    private void checkDownstreamDependencies(String planId, PlanExecutionState state) {
        state.getAllTasks().stream()
                .filter(t -> t.status().get() == TaskStatus.PENDING)
                .filter(t -> t.isReady(state.getAllTasks()))
                .forEach(t -> {
                    t.status().set(TaskStatus.READY);
                    eventPublisher.publishEvent(new TaskReadyEvent(planId, t));
                });
    }

    private void checkPlanCompletion(String planId, PlanExecutionState state) {
        boolean allTerminal = state.getAllTasks().stream().allMatch(t -> {
            TaskStatus status = t.status().get();
            return status == TaskStatus.COMPLETED || status == TaskStatus.FAILED || status == TaskStatus.BLOCKED;
        });

        if (allTerminal) {
            log.info("Plan {} has reached terminal state.", planId);
            eventPublisher.publishEvent(new PlanCompletedEvent(planId, state.getResults()));
            activePlans.remove(planId);
        }
    }

    private static class PlanExecutionState {
        private final List<DagTask> tasks;
        private final Map<String, String> results = new ConcurrentHashMap<>();

        public PlanExecutionState(List<DagTask> tasks) {
            this.tasks = tasks;
        }

        public List<DagTask> getAllTasks() { return tasks; }

        public DagTask getTask(String taskId) {
            return tasks.stream().filter(t -> t.id().equals(taskId)).findFirst().orElseThrow();
        }

        public void addResult(String taskId, String result) {
            results.put(taskId, result);
        }

        public Map<String, String> getResults() { return results; }
    }
}