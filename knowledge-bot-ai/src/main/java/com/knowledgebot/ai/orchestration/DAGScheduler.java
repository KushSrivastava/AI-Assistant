package com.knowledgebot.ai.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Stuck-state detection: max retries per task before giving up


@Service
public class DAGScheduler {

    private static final Logger log = LoggerFactory.getLogger(DAGScheduler.class);
    private static final Pattern TASK_LINE = Pattern.compile("^\\d+\\.\\s*\\[.*?]\\s*(.+)$", Pattern.MULTILINE);
    private static final int MAX_RETRIES_PER_TASK = 3;

    private final List<DagTask> taskGraph = new ArrayList<>();
    private final Map<String, String> taskResults = new ConcurrentHashMap<>();
    // Per-task stuck-state detectors, keyed by task ID
    private final Map<String, StuckStateDetector> detectors = new ConcurrentHashMap<>();

    public List<DagTask> buildGraphFromPlan(String markdownPlan) {
        taskGraph.clear();
        detectors.clear();
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
        return List.copyOf(taskGraph);
    }

    public List<DagTask> buildGraphWithParallelTasks(List<String> independentTasks, List<String> sequentialTasks) {
        taskGraph.clear();
        detectors.clear();
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
        return List.copyOf(taskGraph);
    }

    public Map<String, String> executeAll(ChatClient chatClient, long timeoutSeconds) {
        CountDownLatch latch = new CountDownLatch(taskGraph.size());

        for (DagTask task : taskGraph) {
            Thread.startVirtualThread(() -> {
                try {
                    waitForDependencies(task);
                    if (task.hasFailedDependencies(taskGraph)) {
                        task.status().set(TaskStatus.BLOCKED);
                        log.warn("Task {} blocked due to failed dependencies", task.id());
                        return;
                    }
                    executeTask(task, chatClient);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    task.status().set(TaskStatus.FAILED);
                    log.error("Task {} interrupted: {}", task.id(), e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            boolean completed = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("DAG execution timed out after {} seconds", timeoutSeconds);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("DAG execution interrupted");
        }

        for (DagTask task : taskGraph) {
            taskResults.put(task.id(), task.result().get());
        }

        return Map.copyOf(taskResults);
    }

    private void waitForDependencies(DagTask task) throws InterruptedException {
        while (!task.isReady(taskGraph)) {
            if (task.hasFailedDependencies(taskGraph)) return;
            Thread.sleep(100);
        }
    }

    private void executeTask(DagTask task, ChatClient chatClient) {
        task.status().set(TaskStatus.EXECUTING);
        log.info("Executing task {}: {}", task.id(), task.description());

        StuckStateDetector detector = detectors.computeIfAbsent(task.id(), k -> new StuckStateDetector());

        for (int attempt = 1; attempt <= MAX_RETRIES_PER_TASK; attempt++) {
            try {
                String prompt = buildPrompt(task.description(), attempt);
                String result = chatClient.prompt().user(prompt).call().content();

                if (detector.record(result)) {
                    // Stuck: last N outputs were near-identical — inject recovery strategy
                    log.warn("Task {} is STUCK after {} attempts. Injecting recovery prompt.", task.id(), attempt);
                    if (attempt < MAX_RETRIES_PER_TASK) {
                        log.info("Retrying task {} with recovery prompt...", task.id());
                        continue;  // next iteration will use buildPrompt with attempt > 1
                    }
                    // Exhausted retries — still stuck
                    task.result().set("[STUCK DETECTION] Task produced identical outputs " +
                            MAX_RETRIES_PER_TASK + " times. Last output:\n" + result);
                    task.status().set(TaskStatus.FAILED);
                    return;
                }

                task.result().set(result);
                task.status().set(TaskStatus.COMPLETED);
                log.info("Task {} completed on attempt {}", task.id(), attempt);
                return;

            } catch (Exception e) {
                log.error("Task {} attempt {} failed: {}", task.id(), attempt, e.getMessage());
                if (attempt == MAX_RETRIES_PER_TASK) {
                    task.result().set("Error after " + attempt + " attempts: " + e.getMessage());
                    task.status().set(TaskStatus.FAILED);
                }
            }
        }
    }

    private static String buildPrompt(String description, int attempt) {
        if (attempt == 1) {
            return "Execute the following task. Provide a detailed, actionable result:\n\nTask: " + description;
        }
        // Recovery prompt — change strategy explicitly
        return """
            PREVIOUS ATTEMPTS PRODUCED IDENTICAL RESULTS — CHANGE YOUR STRATEGY.
            Try a completely different approach or break the task into smaller sub-steps.

            Task: %s

            Requirements:
            - Do NOT repeat your previous answer.
            - Think step-by-step.
            - If blocked, state what is blocking you and propose an alternative.
            """.formatted(description);
    }

    public List<DagTask> getTaskGraph() {
        return List.copyOf(taskGraph);
    }

    public String getProgressReport() {
        long total = taskGraph.size();
        long completed = taskGraph.stream().filter(t -> t.status().get() == TaskStatus.COMPLETED).count();
        long failed = taskGraph.stream().filter(t -> t.status().get() == TaskStatus.FAILED).count();
        long executing = taskGraph.stream().filter(t -> t.status().get() == TaskStatus.EXECUTING).count();
        long pending = taskGraph.stream().filter(t -> t.status().get() == TaskStatus.PENDING).count();

        return String.format("Progress: %d/%d completed | %d executing | %d pending | %d failed",
                completed, total, executing, pending, failed);
    }
}
