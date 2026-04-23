package com.knowledgebot.ai.architect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ArchitectService {

    private static final Logger log = LoggerFactory.getLogger(ArchitectService.class);
    private final ChatClient chatClient;

    public ArchitectService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public record ArchitectResult(String goal, String hld, String lld, String implementation) {}

    public ArchitectResult architect(String goal) {
        String hld = generateHLD(goal);
        String lld = generateLLD(hld);
        String implementation = generateImplementationPlan(lld);
        return new ArchitectResult(goal, hld, lld, implementation);
    }

    public String generateHLD(String requirements) {
        log.info("Generating High-Level Design (HLD)");
        return chatClient.prompt()
                .system("You are a Senior System Architect. Create a High-Level Design (HLD) for the following requirements. Focus on architecture, data flow, and technology stack.")
                .user(requirements)
                .call()
                .content();
    }

    public String generateLLD(String hld) {
        log.info("Generating Low-Level Design (LLD)");
        return chatClient.prompt()
                .system("You are a Senior Software Engineer. Based on the provided HLD, create a detailed Low-Level Design (LLD). Define class structures, interfaces, and specific algorithm details.")
                .user(hld)
                .call()
                .content();
    }

    public String generateImplementationPlan(String lld) {
        log.info("Generating Implementation Plan");
        return chatClient.prompt()
                .system("You are a Lead Developer. Based on the LLD, create a step-by-step implementation plan for the autonomous agent. List specific files to create/modify.")
                .user(lld)
                .call()
                .content();
    }
}
