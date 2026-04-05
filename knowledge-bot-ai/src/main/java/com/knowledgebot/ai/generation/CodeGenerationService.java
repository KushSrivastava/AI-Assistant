package com.knowledgebot.ai.generation;

import com.knowledgebot.ai.model.WorkspaceManager;
import com.knowledgebot.core.language.LanguageDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Service
public class CodeGenerationService {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationService.class);

    private final ChatClient chatClient;
    private final LanguageDetectionService languageDetector;
    private final PromptTemplateRegistry templateRegistry;
    private final WorkspaceManager workspaceManager;

    public CodeGenerationService(ChatClient.Builder chatClientBuilder, 
                                 LanguageDetectionService languageDetector, 
                                 PromptTemplateRegistry templateRegistry,
                                 WorkspaceManager workspaceManager) {
        this.chatClient = chatClientBuilder.build();
        this.languageDetector = languageDetector;
        this.templateRegistry = templateRegistry;
        this.workspaceManager = workspaceManager;
    }

    public String generate(String instructions, Path targetFileContext) {
        String language = targetFileContext != null ? languageDetector.detectLanguage(targetFileContext) : "unknown";
        String systemInstruction = templateRegistry.getSystemPromptForLanguage(language);

        String generatedCode = chatClient.prompt()
                .system(systemInstruction)
                .user(instructions)
                .call()
                .content();

        if (targetFileContext != null) {
            try {
                Path parent = targetFileContext.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                Files.writeString(targetFileContext, generatedCode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                log.info("Successfully wrote generated code to: {}", targetFileContext);
            } catch (IOException e) {
                log.error("Failed to write code to {}: {}", targetFileContext, e.getMessage());
                throw new RuntimeException("Failed to write to file: " + targetFileContext, e);
            }
        }

        return generatedCode;
    }
}
