package com.knowledgebot.ai.orchestration;

import com.knowledgebot.ai.orchestration.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import com.knowledgebot.ai.orchestration.events.OrchestrationEvent.*;

@Component
public class TaskExecutionWorker {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionWorker.class);
    private static final int MAX_RETRIES_PER_TASK = 3;

    private final ApplicationEventPublisher eventPublisher;
    // Note: You can pass a ChatClient factory or builder here if you need MCP tools dynamically injected
    private final ChatClient chatClient;

    public TaskExecutionWorker(ApplicationEventPublisher eventPublisher, ChatClient.Builder chatClientBuilder) {
        this.eventPublisher = eventPublisher;
        this.chatClient = chatClientBuilder.build();
    }

    @Async // This now uses Virtual Threads!
    @EventListener
    public void onTaskReady(TaskReadyEvent event) {
        DagTask task = event.task();
        String planId = event.planId();

        eventPublisher.publishEvent(new TaskStartedEvent(planId, task.id()));
        log.info("[{}] Executing task {}: {}", planId, task.id(), task.description());

        StuckStateDetector detector = new StuckStateDetector();

        for (int attempt = 1; attempt <= MAX_RETRIES_PER_TASK; attempt++) {
            try {
                String prompt = buildPrompt(task.description(), attempt);
                String result = chatClient.prompt().user(prompt).call().content();

                if (detector.record(result)) {
                    log.warn("[{}] Task {} is STUCK after {} attempts.", planId, task.id(), attempt);
                    if (attempt < MAX_RETRIES_PER_TASK) continue;

                    eventPublisher.publishEvent(new TaskFailedEvent(planId, task.id(),
                            "[STUCK DETECTION] Task produced identical outputs."));
                    return;
                }

                log.info("[{}] Task {} completed.", planId, task.id());
                eventPublisher.publishEvent(new TaskCompletedEvent(planId, task.id(), result));
                return;

            } catch (Exception e) {
                log.error("[{}] Task {} attempt {} failed: {}", planId, task.id(), attempt, e.getMessage());
                if (attempt == MAX_RETRIES_PER_TASK) {
                    eventPublisher.publishEvent(new TaskFailedEvent(planId, task.id(), e.getMessage()));
                }
            }
        }
    }

    private static String buildPrompt(String description, int attempt) {
        if (attempt == 1) {
            return "Execute the following task. Provide a detailed, actionable result:\n\nTask: " + description;
        }
        return """
            PREVIOUS ATTEMPTS PRODUCED IDENTICAL RESULTS — CHANGE YOUR STRATEGY.
            Try a completely different approach or break the task into smaller sub-steps.

            Task: %s
            """.formatted(description);
    }
}