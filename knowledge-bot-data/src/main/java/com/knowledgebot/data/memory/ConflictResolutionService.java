package com.knowledgebot.data.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Logic-adapted from the user's recommendations: Implements Conflict Resolution.
 * For "Global Knowledge," resolves differences between local instances' information.
 */
@Service
public class ConflictResolutionService {
    private static final Logger log = LoggerFactory.getLogger(ConflictResolutionService.class);
    private final ChatClient chatClient;

    public ConflictResolutionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Resolves conflicts between a new fact and existing global knowledge.
     * @param newFact The new incoming knowledge
     * @param existingFacts List of existing related facts
     * @return The "Source of Truth" resolved version or a merged version
     */
    public String resolve(String newFact, List<Document> existingFacts) {
        if (existingFacts.isEmpty()) {
            return newFact;
        }

        log.info("Resolving knowledge conflict between new fact and {} existing records.", existingFacts.size());

        String existingContent = existingFacts.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n---\n" + b);

        String resolutionPrompt = String.format("""
                You are a Knowledge Merger (Source of Truth Hierarchy).
                Compare the NEW FACT with EXISTING KNOWLEDGE.

                NEW FACT: %s
                EXISTING KNOWLEDGE: %s

                If they conflict (e.g., different versions of a library), prioritize the NEW FACT if it seems more recent.
                If they are complementary, merge them into a single coherent entry.
                Return only the resolved/merged content.
                """, newFact, existingContent);

        return chatClient.prompt()
                .user(resolutionPrompt)
                .call()
                .content();
    }
}
