package com.knowledgebot.ai.optimization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ModelWarmupService {
    private static final Logger log = LoggerFactory.getLogger(ModelWarmupService.class);
    private final OllamaChatModel ollamaChatModel;

    // Use a lightweight model for warmup to avoid long load times
    private static final String WARMUP_MODEL = "llama3.2:3b";

    public ModelWarmupService(OllamaChatModel ollamaChatModel) {
        this.ollamaChatModel = ollamaChatModel;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmupModel() {
        log.info("Application ready. Initiating silent AI wake-up ping to preload weights...");
        try {
            // Create a temporary chat to force model loading
            ChatClient warmupClient = ChatClient.builder(ollamaChatModel)
                    .defaultOptions(OllamaChatOptions.builder()
                            .model(WARMUP_MODEL)
                            .temperature(0.0)
                            .build())
                    .build();

            warmupClient.prompt().user("ping").call().content();
            log.info("Model '{}' weights pre-loaded to memory successfully!", WARMUP_MODEL);
        } catch (Exception e) {
            log.warn("Could not reach local Ollama for model warm-up: {}", e.getMessage());
        }
    }
}
