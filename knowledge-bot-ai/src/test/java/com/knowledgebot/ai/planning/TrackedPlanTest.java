package com.knowledgebot.ai.planning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TrackedPlan} — the live plan state machine.
 * Tests step status transitions, progress calculation, and board rendering.
 */
@DisplayName("TrackedPlan")
class TrackedPlanTest {

    private TrackedPlan createPlan(String... steps) {
        return new TrackedPlan(
            "Test Goal",
            "# Test Plan\n",
            List.of(steps)
        );
    }

    @Test
    @DisplayName("All steps should start as NOT_STARTED")
    void shouldInitializeStepsAsNotStarted() {
        TrackedPlan plan = createPlan("Step 1", "Step 2", "Step 3");
        plan.getSteps().forEach(step ->
            assertEquals(TrackedPlan.StepStatus.NOT_STARTED, step.status().get())
        );
    }

    @Test
    @DisplayName("Should mark step as IN_PROGRESS")
    void shouldMarkStepInProgress() {
        TrackedPlan plan = createPlan("Step 1");
        plan.getStep(0).markInProgress();
        assertEquals(TrackedPlan.StepStatus.IN_PROGRESS, plan.getStep(0).status().get());
    }

    @Test
    @DisplayName("Should mark step as COMPLETED with result")
    void shouldMarkStepCompleted() {
        TrackedPlan plan = createPlan("Step 1");
        plan.getStep(0).markCompleted("Done successfully");
        assertEquals(TrackedPlan.StepStatus.COMPLETED, plan.getStep(0).status().get());
        assertEquals("Done successfully", plan.getStep(0).result().get());
        assertNotNull(plan.getStep(0).completedAt().get());
    }

    @Test
    @DisplayName("Should mark step as FAILED with error")
    void shouldMarkStepFailed() {
        TrackedPlan plan = createPlan("Step 1");
        plan.getStep(0).markFailed("Build error: missing import");
        assertEquals(TrackedPlan.StepStatus.FAILED, plan.getStep(0).status().get());
        assertTrue(plan.getStep(0).result().get().contains("Build error"));
    }

    @Test
    @DisplayName("Should calculate 0% completion when no steps are done")
    void shouldCalculateZeroCompletion() {
        TrackedPlan plan = createPlan("Step 1", "Step 2");
        assertEquals(0, plan.getCompletionPercent());
    }

    @Test
    @DisplayName("Should calculate 50% completion when half the steps are done")
    void shouldCalculateHalfCompletion() {
        TrackedPlan plan = createPlan("Step 1", "Step 2");
        plan.getStep(0).markCompleted("done");
        assertEquals(50, plan.getCompletionPercent());
    }

    @Test
    @DisplayName("Should calculate 100% completion when all steps are done")
    void shouldCalculateFullCompletion() {
        TrackedPlan plan = createPlan("Step 1", "Step 2");
        plan.getStep(0).markCompleted("done");
        plan.getStep(1).markCompleted("done");
        assertEquals(100, plan.getCompletionPercent());
        assertTrue(plan.isComplete());
    }

    @Test
    @DisplayName("renderProgressBoard should include goal and step descriptions")
    void shouldRenderProgressBoard() {
        TrackedPlan plan = createPlan("Create controller", "Write tests");
        plan.getStep(0).markCompleted("done");
        String board = plan.renderProgressBoard();
        assertTrue(board.contains("Test Goal"));
        assertTrue(board.contains("Create controller"));
        assertTrue(board.contains("Write tests"));
        assertTrue(board.contains("[x]")); // Completed icon
    }
}
