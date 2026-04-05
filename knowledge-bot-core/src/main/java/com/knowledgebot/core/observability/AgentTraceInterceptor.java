package com.knowledgebot.core.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Logic-adapted from the user's recommendations: Implements Observability & Traceability.
 * Tracks the agentic lifecycle using Micrometer.
 */
@Component
public class AgentTraceInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AgentTraceInterceptor.class);
    private final Tracer tracer;

    public AgentTraceInterceptor(@Nullable Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Starts a new trace span for an agentic task.
     * @param taskName Name of the task (e.g., "Planning", "Reflection")
     * @return The started span, or a no-op span if tracing is not configured
     */
    public Span startSpan(String taskName) {
        if (tracer == null) {
            log.debug("Tracing not configured, skipping span creation for: {}", taskName);
            return NoOpSpan.INSTANCE;
        }
        log.info("Starting agentic trace span: {}", taskName);
        return this.tracer.nextSpan().name(taskName).start();
    }

    /**
     * Ends a span and logs the completion.
     * @param span The span to end
     */
    public void endSpan(Span span) {
        if (span != null && span != NoOpSpan.INSTANCE) {
            log.info("Ending agentic trace span: {}", span.context().spanId());
            span.end();
        }
    }

    /**
     * A no-op Span implementation used when tracing is not configured.
     * This eliminates the need for null checks in calling code.
     */
    private static class NoOpSpan implements Span {
        static final NoOpSpan INSTANCE = new NoOpSpan();

        private NoOpSpan() {}

        @Override
        public boolean isNoop() {
            return true;
        }

        @Override
        public Span start() {
            return this;
        }

        @Override
        public TraceContext context() {
            return NoOpTraceContext.INSTANCE;
        }

        @Override
        public Span remoteServiceName(String remoteServiceName) {
            return this;
        }

        @Override
        public Span remoteIpAndPort(String remoteIp, int remotePort) {
            return this;
        }

        @Override
        public Span name(String name) {
            return this;
        }

        @Override
        public Span event(String value) {
            return this;
        }

        @Override
        public Span event(String value, long timestamp, TimeUnit timeUnit) {
            return this;
        }

        @Override
        public Span tag(String key, String value) {
            return this;
        }

        @Override
        public Span error(Throwable exception) {
            return this;
        }

        @Override
        public void end() {
            // no-op
        }

        @Override
        public void end(long timestamp, TimeUnit timeUnit) {
            // no-op
        }

        @Override
        public void abandon() {
            // no-op
        }
    }

    /**
     * A no-op TraceContext implementation used by NoOpSpan.
     */
    private static class NoOpTraceContext implements TraceContext {
        static final NoOpTraceContext INSTANCE = new NoOpTraceContext();

        private NoOpTraceContext() {}

        @Override
        @Nullable
        public String traceId() {
            return "no-op";
        }

        @Override
        @Nullable
        public String spanId() {
            return "no-op";
        }

        @Override
        @Nullable
        public String parentId() {
            return null;
        }

        @Override
        @Nullable
        public Boolean sampled() {
            return Boolean.FALSE;
        }
    }
}
