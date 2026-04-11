package com.knowledgebot.ai.orchestration;

import com.knowledgebot.ai.orchestration.events.*;
import com.knowledgebot.core.observability.AgentTraceInterceptor;
import com.knowledgebot.core.observability.TrainingExperience;
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
    private final ChatClient chatClient;

    // Inject our new Phase 5 Telemetry Interceptor
    private final AgentTraceInterceptor traceInterceptor;

    public TaskExecutionWorker(ApplicationEventPublisher eventPublisher,
                               ChatClient.Builder chatClientBuilder,
                               AgentTraceInterceptor traceInterceptor) {
        this.eventPublisher = eventPublisher;
        this.chatClient = chatClientBuilder.build();
        this.traceInterceptor = traceInterceptor;
    }

    @Async
    @EventListener
    public void onTaskReady(OrchestrationEvent.TaskReadyEvent event) {
        DagTask task = event.task();
        String planId = event.planId();

        eventPublisher.publishEvent(new OrchestrationEvent.TaskStartedEvent(planId, task.id()));
        log.info("[{}] Executing task {}: {}", planId, task.id(), task.description());

        // Initialize our new memory-tracking detector
        StuckStateDetector detector = new StuckStateDetector();

        for (int attempt = 1; attempt <= MAX_RETRIES_PER_TASK; attempt++) {
            try {
                String prompt = buildPrompt(task.description(), attempt);
                String result = chatClient.prompt().user(prompt).call().content();

                if (detector.recordAttempt(prompt, result)) {
                    log.warn("[{}] Task {} is STUCK after {} attempts.", planId, task.id(), attempt);
                    if (attempt < MAX_RETRIES_PER_TASK) continue; // Try again!

                    eventPublisher.publishEvent(new TaskFailedEvent(planId, task.id(),
                            "[STUCK DETECTION] Task produced identical outputs."));
                    return;
                }

                // ==========================================
                // PHASE 5: LOCAL DATA LOOP TRIGGERS HERE!
                // ==========================================
                // We reached a success! Was it a triumph over failure, or just a standard win?
                String datasetType = detector.isCorrectionTriumph() ? "CORRECTION_TRIUMPH" : "STANDARD_EXECUTION";

                // Fire and forget to the JSONL builder!
                traceInterceptor.recordExperience(
                        new TrainingExperience(datasetType, task.id(), detector.getDatasetMessages())
                );

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