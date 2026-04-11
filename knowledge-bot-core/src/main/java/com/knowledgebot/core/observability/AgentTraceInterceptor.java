package com.knowledgebot.core.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

/**
 * Logic-adapted from the user's recommendations: Implements Observability & Traceability.
 * Tracks the agentic lifecycle using Micrometer, and persists training datasets.
 */
@Component
public class AgentTraceInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AgentTraceInterceptor.class);

    private final Tracer tracer;

    // --- Phase 5 Dependencies ---
    private static final Path JSONL_PATH = Paths.get("training_experiences.jsonl");
    private final ObjectMapper objectMapper;

    // Updated Constructor to handle both Observability and the Data Loop
    public AgentTraceInterceptor(@Nullable Tracer tracer) {
        this.tracer = tracer;
        this.objectMapper = new ObjectMapper(); // Instantiated directly here
    }

    // ========================================================================
    // NEW PHASE 5 METHODS (Data Loop)
    // ========================================================================

    /**
     * Asynchronously serializes an execution trace and appends it to the local dataset.
     */
    @Async
    public void recordExperience(TrainingExperience experience) {
        try {
            if (!Files.exists(JSONL_PATH)) {
                Files.createFile(JSONL_PATH);
                log.info("Created new local training dataset: {}", JSONL_PATH.toAbsolutePath());
            }

            String jsonLine = objectMapper.writeValueAsString(experience) + System.lineSeparator();

            Files.writeString(JSONL_PATH, jsonLine, StandardOpenOption.APPEND);

            if ("CORRECTION_TRIUMPH".equals(experience.datasetType())) {
                log.info("🌟 [Data Loop] High-Value Correction Triumph captured and appended to local dataset!");
            } else {
                log.debug("💾 [Data Loop] Standard trace captured.");
            }

        } catch (IOException e) {
            log.error("Failed to write training trace to JSONL", e);
        }
    }

    // ========================================================================
    // ORIGINAL METHODS PRESERVED BELOW (Micrometer Tracing)
    // ========================================================================

    public Span startSpan(String taskName) {
        if (tracer == null) {
            log.debug("Tracing not configured, skipping span creation for: {}", taskName);
            return NoOpSpan.INSTANCE;
        }
        log.info("Starting agentic trace span: {}", taskName);
        return this.tracer.nextSpan().name(taskName).start();
    }

    public void endSpan(Span span) {
        if (span != null && span != NoOpSpan.INSTANCE) {
            log.info("Ending agentic trace span: {}", span.context().spanId());
            span.end();
        }
    }

    private static class NoOpSpan implements Span {
        static final NoOpSpan INSTANCE = new NoOpSpan();
        private NoOpSpan() {}
        @Override public boolean isNoop() { return true; }
        @Override public Span start() { return this; }
        @Override public TraceContext context() { return NoOpTraceContext.INSTANCE; }
        @Override public Span remoteServiceName(String remoteServiceName) { return this; }
        @Override public Span remoteIpAndPort(String remoteIp, int remotePort) { return this; }
        @Override public Span name(String name) { return this; }
        @Override public Span event(String value) { return this; }
        @Override public Span event(String value, long timestamp, TimeUnit timeUnit) { return this; }
        @Override public Span tag(String key, String value) { return this; }
        @Override public Span error(Throwable exception) { return this; }
        @Override public void end() { }
        @Override public void end(long timestamp, TimeUnit timeUnit) { }
        @Override public void abandon() { }
    }

    private static class NoOpTraceContext implements TraceContext {
        static final NoOpTraceContext INSTANCE = new NoOpTraceContext();
        private NoOpTraceContext() {}
        @Override @Nullable public String traceId() { return "no-op"; }
        @Override @Nullable public String spanId() { return "no-op"; }
        @Override @Nullable public String parentId() { return null; }
        @Override @Nullable public Boolean sampled() { return Boolean.FALSE; }
    }
}