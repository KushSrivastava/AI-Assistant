package com.knowledgebot.ai.multimodal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

/**
 * Logic-adapted from OpenCode: Implements Multimodal UI-to-Code generation.
 * Uses local vision models (Llava/Qwen-VL) via Ollama.
 */
@Service
public class MultimodalService {
    private static final Logger log = LoggerFactory.getLogger(MultimodalService.class);

    private final ChatClient chatClient;

    public MultimodalService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Analyzes an image and generates code based on it.
     * @param imageData The image bytes
     * @param contentType The MIME type (e.g., image/png)
     * @param prompt The user's request (e.g., "Build an Angular component for this login screen")
     * @return Generated code snippet
     */
    public String analyzeImageToCode(byte[] imageData, String contentType, String prompt) {
        log.info("Analyzing multimodal input of type {} with prompt: {}", contentType, prompt);

        return chatClient.prompt()
                .user(u -> u.text(prompt)
                        .media(MimeTypeUtils.parseMimeType(contentType), new ByteArrayResource(imageData)))
                .call()
                .content();
    }
}
