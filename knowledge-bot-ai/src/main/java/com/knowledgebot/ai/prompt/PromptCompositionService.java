package com.knowledgebot.ai.prompt;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PromptCompositionService {

    private static final String DEFAULT_QA_TEMPLATE = 
        "You are an expert developer assistant. Use the following code context to answer the user's question.\n" +
        "If the answer is not in the context, use your best programming knowledge.\n\n" +
        "CONTEXT:\n{context}\n\n" +
        "QUESTION: {question}";

    public Prompt buildPrompt(String userQuestion, List<Document> contextDocuments) {
        String contextString = contextDocuments.stream()
                .map(d -> "--- File: " + d.getMetadata().getOrDefault("fileName", "Unknown") + " ---\n" + d.getText())
                .collect(Collectors.joining("\n\n"));

        PromptTemplate template = new PromptTemplate(DEFAULT_QA_TEMPLATE);
        return template.create(Map.of(
            "context", contextString,
            "question", userQuestion
        ));
    }
}
