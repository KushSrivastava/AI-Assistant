package com.knowledgebot.ai.planning;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logic-adapted from OpenCode: Implements a "Plan-First" architecture.
 * Generates a Markdown task list for user approval before execution.
 *
 * Extended with OpenManus-style per-step status tracking via {@link TrackedPlan}.
 */
@Service
public class PlanningService {

    private final ChatClient chatClient;

    /** Matches numbered markdown task lines: "1. [ ] Description" or "1. Description" */
    private static final Pattern TASK_LINE =
            Pattern.compile("^\\d+\\.\\s*(?:\\[.*?]\\s*)?(.+)$", Pattern.MULTILINE);

    public PlanningService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    // -------------------------------------------------------------------------
    // Core generation
    // -------------------------------------------------------------------------

    /**
     * Generates a markdown plan for the given intent and returns it as raw text.
     * Use {@link #generateTrackedPlan(String)} if you need per-step lifecycle tracking.
     */
    public String generatePlan(String userIntent) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userIntent)
                .call()
                .content();
    }

    /**
     * Generates a plan and wraps it in a {@link TrackedPlan} with per-step
     * NOT_STARTED → IN_PROGRESS → COMPLETED lifecycle tracking.
     *
     * This is the preferred entry-point when you intend to execute the plan
     * via {@link com.knowledgebot.ai.orchestration.OrchestratorService}.
     */
    public TrackedPlan generateTrackedPlan(String userIntent) {
        String markdown = generatePlan(userIntent);
        List<String> steps = parseSteps(markdown);
        return new TrackedPlan(userIntent, markdown, steps);
    }

    // -------------------------------------------------------------------------

    /** Parse numbered task descriptions from markdown. */
    public static List<String> parseSteps(String markdown) {
        List<String> steps = new ArrayList<>();
        Matcher m = TASK_LINE.matcher(markdown);
        while (m.find()) {
            String desc = m.group(1).trim();
            if (!desc.isEmpty()) steps.add(desc);
        }
        return steps;
    }

    // -------------------------------------------------------------------------

    private static final String SYSTEM_PROMPT = """
            You are a Technical Orchestrator for the Knowledge Bot.
            Your job is to take a user's intent and break it down into a clear, numbered Markdown task list.
            Do NOT execute anything yet. Just propose the PLAN.

            Format:
            ### Proposed Plan
            1. [ ] Task 1 Description
            2. [ ] Task 2 Description
            ...
            """;
}
