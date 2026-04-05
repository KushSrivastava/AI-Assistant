package com.knowledgebot.ai.modernize;

import com.knowledgebot.core.analyzer.LegacyCodeAnalyzer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DiffGenerationService {

    private final ChatClient chatClient;
    private final LegacyCodeAnalyzer legacyAnalyzer;

    public DiffGenerationService(ChatClient.Builder chatClientBuilder, LegacyCodeAnalyzer legacyAnalyzer) {
        this.chatClient = chatClientBuilder.build();
        this.legacyAnalyzer = legacyAnalyzer;
    }

    public String modernize(Path file) throws IOException {
        String astContext = legacyAnalyzer.analyzeForModernization(file);
        String originalCode = Files.readString(file);

        String prompt = "Modernize the following Java source code to Java 25 idioms.\n" +
                "Focus on replacing Getter/Setters with Records, old switches with Pattern Matching, and ThreadLocal with ScopedValues.\n" +
                "AST Context: " + astContext + "\n\n" +
                "Source Code:\n" + originalCode;

        return chatClient.prompt()
                .system("You are a Java Refactoring expert.")
                .user(prompt)
                .call()
                .content();
    }
}
