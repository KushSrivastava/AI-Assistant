package com.knowledgebot.ai.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/**
 * Spring AI CallAdvisor that records per-model latency and error metrics into
 * ModelMetrics.  Attached to every ChatClient built by ChatClientFactory so
 * ModelRouterService gets live feedback for adaptive scoring.
 */
public class MetricsAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(MetricsAdvisor.class);

    private final String modelName;
    private final ModelMetrics metrics;

    public MetricsAdvisor(String modelName, ModelMetrics metrics) {
        this.modelName = modelName;
        this.metrics = metrics;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        try {
            ChatClientResponse response = chain.nextCall(request);
            long latency = System.currentTimeMillis() - start;
            metrics.recordSuccess(latency);
            log.debug("Model {} responded in {}ms", modelName, latency);
            return response;
        } catch (Exception e) {
            metrics.recordError();
            log.warn("Model {} call failed: {}", modelName, e.getMessage());
            throw e;
        }
    }

    @Override
    public String getName() {
        return "MetricsAdvisor[" + modelName + "]";
    }

    @Override
    public int getOrder() {
        // Run last so we measure total round-trip including other advisors
        return LOWEST_PRECEDENCE;
    }
}
