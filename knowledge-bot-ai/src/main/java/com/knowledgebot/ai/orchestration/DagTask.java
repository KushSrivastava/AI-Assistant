package com.knowledgebot.ai.orchestration;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public record DagTask(
    String id,
    String description,
    List<String> dependencies,
    AtomicReference<TaskStatus> status,
    AtomicReference<String> result
) {
    public DagTask(String id, String description, List<String> dependencies) {
        this(id, description, dependencies, new AtomicReference<>(TaskStatus.PENDING), new AtomicReference<>(""));
    }

    public boolean isReady(List<DagTask> allTasks) {
        TaskStatus current = status.get();
        if (current != TaskStatus.PENDING && current != TaskStatus.BLOCKED) return false;
        return dependencies.stream()
                .allMatch(depId -> allTasks.stream()
                        .filter(t -> t.id().equals(depId))
                        .findFirst()
                        .map(t -> t.status().get() == TaskStatus.COMPLETED)
                        .orElse(false));
    }

    public boolean hasFailedDependencies(List<DagTask> allTasks) {
        return dependencies.stream()
                .anyMatch(depId -> allTasks.stream()
                        .filter(t -> t.id().equals(depId))
                        .findFirst()
                        .map(t -> t.status().get() == TaskStatus.FAILED)
                        .orElse(false));
    }
}
