package com.knowledgebot.ai.planning;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A live plan: parsed from PlanningService's markdown output, backed by per-step
 * status that is updated as DAGScheduler executes each step.
 *
 * Step lifecycle mirrors OpenManus's planning system:
 *   NOT_STARTED → IN_PROGRESS → COMPLETED | BLOCKED | FAILED
 */
public class TrackedPlan {

    public enum StepStatus {
        NOT_STARTED, IN_PROGRESS, COMPLETED, BLOCKED, FAILED
    }

    public record Step(
            int index,
            String description,
            AtomicReference<StepStatus> status,
            AtomicReference<String> result,
            Instant startedAt,
            AtomicReference<Instant> completedAt
    ) {
        public Step(int index, String description) {
            this(index, description,
                    new AtomicReference<>(StepStatus.NOT_STARTED),
                    new AtomicReference<>(""),
                    Instant.now(),
                    new AtomicReference<>(null));
        }

        public void markInProgress() {
            status.set(StepStatus.IN_PROGRESS);
        }

        public void markCompleted(String output) {
            result.set(output);
            status.set(StepStatus.COMPLETED);
            completedAt.set(Instant.now());
        }

        public void markFailed(String error) {
            result.set(error);
            status.set(StepStatus.FAILED);
            completedAt.set(Instant.now());
        }

        public void markBlocked() {
            status.set(StepStatus.BLOCKED);
        }

        public String statusIcon() {
            return switch (status.get()) {
                case NOT_STARTED -> "[ ]";
                case IN_PROGRESS -> "[>]";
                case COMPLETED   -> "[x]";
                case BLOCKED     -> "[!]";
                case FAILED      -> "[✗]";
            };
        }
    }

    private final String goal;
    private final String rawMarkdown;
    private final List<Step> steps = new CopyOnWriteArrayList<>();
    private final Instant createdAt = Instant.now();

    public TrackedPlan(String goal, String rawMarkdown, List<String> stepDescriptions) {
        this.goal = goal;
        this.rawMarkdown = rawMarkdown;
        for (int i = 0; i < stepDescriptions.size(); i++) {
            steps.add(new Step(i, stepDescriptions.get(i)));
        }
    }

    // -------------------------------------------------------------------------

    public String getGoal() { return goal; }
    public String getRawMarkdown() { return rawMarkdown; }
    public List<Step> getSteps() { return List.copyOf(steps); }

    public Step getStep(int index) { return steps.get(index); }

    // -------------------------------------------------------------------------

    /** Render a live progress board similar to OpenManus's plan viewer. */
    public String renderProgressBoard() {
        long total     = steps.size();
        long completed = steps.stream().filter(s -> s.status().get() == StepStatus.COMPLETED).count();
        long failed    = steps.stream().filter(s -> s.status().get() == StepStatus.FAILED).count();
        long blocked   = steps.stream().filter(s -> s.status().get() == StepStatus.BLOCKED).count();
        long running   = steps.stream().filter(s -> s.status().get() == StepStatus.IN_PROGRESS).count();

        int pct = total == 0 ? 0 : (int) (completed * 100 / total);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== Plan: %s ===%n", goal));
        sb.append(String.format("Progress: %d%% (%d/%d) | running=%d blocked=%d failed=%d%n%n",
                pct, completed, total, running, blocked, failed));

        for (Step step : steps) {
            sb.append(String.format("  %s %d. %s%n", step.statusIcon(), step.index() + 1, step.description()));
            String result = step.result().get();
            if (!result.isEmpty() && step.status().get() != StepStatus.NOT_STARTED) {
                // Truncate long results in the board view
                String preview = result.length() > 120 ? result.substring(0, 120) + "…" : result;
                sb.append(String.format("       → %s%n", preview));
            }
        }
        return sb.toString();
    }

    public boolean isComplete() {
        return steps.stream().noneMatch(s ->
                s.status().get() == StepStatus.NOT_STARTED ||
                s.status().get() == StepStatus.IN_PROGRESS);
    }

    public int getCompletionPercent() {
        if (steps.isEmpty()) return 0;
        long completed = steps.stream().filter(s -> s.status().get() == StepStatus.COMPLETED).count();
        return (int) (completed * 100 / steps.size());
    }
}
