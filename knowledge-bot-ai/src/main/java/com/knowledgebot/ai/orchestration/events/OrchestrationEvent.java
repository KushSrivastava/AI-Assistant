package com.knowledgebot.ai.orchestration.events;

import com.knowledgebot.ai.orchestration.DagTask;
import java.util.Map;

public interface OrchestrationEvent {
    String planId();

    // Nested records! This perfectly groups them in one file legally.
    record TaskReadyEvent(String planId, DagTask task) implements OrchestrationEvent {}
    record TaskStartedEvent(String planId, String taskId) implements OrchestrationEvent {}
    record TaskCompletedEvent(String planId, String taskId, String result) implements OrchestrationEvent {}
    record TaskFailedEvent(String planId, String taskId, String error) implements OrchestrationEvent {}
    record PlanCompletedEvent(String planId, Map<String, String> finalResults) implements OrchestrationEvent {}
}