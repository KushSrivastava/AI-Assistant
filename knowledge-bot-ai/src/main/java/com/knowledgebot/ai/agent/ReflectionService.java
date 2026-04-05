package com.knowledgebot.ai.agent;

import com.knowledgebot.ai.prompt.ContextPruningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
public class ReflectionService {
    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);
    private static final int CONTEXT_PRUNING_THRESHOLD = 3;

    private final ChatClient chatClient;
    private final Function<TestExecutionTool.TestRequest, TestExecutionTool.TestResponse> testRunner;
    private final ContextPruningService contextPruningService;

    public ReflectionService(ChatClient.Builder chatClientBuilder, TestExecutionTool testTool, ContextPruningService contextPruningService) {
        this.chatClient = chatClientBuilder.build();
        this.testRunner = testTool.runTests();
        this.contextPruningService = contextPruningService;
    }

    public String reflectAndFix(String originalIssue, String failedCode, String errorLogs) {
        log.info("Starting Reflection Loop for failed build/test.");
        
        String reflectionPrompt = """
            You are a Self-Correction Agent.
            The following code was generated but FAILED validation tests.
            
            ORIGINAL INTENT: {intent}
            FAILED CODE: {code}
            ERROR LOGS: {logs}
            
            Analyze the error carefully. Propose a FIXED version of the code.
            """;
            
        String userPrompt = reflectionPrompt
                .replace("{intent}", originalIssue)
                .replace("{code}", failedCode)
                .replace("{logs}", errorLogs);
        return chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();
    }

    public String executeWithReflection(String intent, String initialSource, int maxRetries) {
        log.info("Starting Reflection Loop for intent: {}", intent);
        
        String currentSource = initialSource;
        List<String> iterationHistory = new ArrayList<>();
        
        for (int i = 0; i < maxRetries; i++) {
            log.info("Reflection Loop Attempt {}/{}", i + 1, maxRetries);
            
            if (iterationHistory.size() >= CONTEXT_PRUNING_THRESHOLD) {
                log.info("Context growing large ({} entries), pruning before next iteration", iterationHistory.size());
                iterationHistory = contextPruningService.prune(iterationHistory, 8000);
            }
            
            TestExecutionTool.TestResponse response = testRunner.apply(new TestExecutionTool.TestRequest("mvn test", "."));
            
            if (response.success()) {
                log.info("Tests PASSED autonomously after {} attempts.", i + 1);
                return currentSource;
            }
            
            log.warn("Tests FAILED. Retrying with reflection...");
            
            String iterationContext = "Attempt " + (i + 1) + " | Error: " + response.output().substring(0, Math.min(500, response.output().length()));
            iterationHistory.add(iterationContext);
            
            String prunedHistory = String.join("\n", iterationHistory);
            String truncatedLogs = response.output().length() > 2000 ? response.output().substring(0, 2000) + "\n...[truncated]" : response.output();
            
            currentSource = reflectAndFix(intent + "\n\nPREVIOUS ATTEMPTS:\n" + prunedHistory, currentSource, truncatedLogs);
        }
        return "[STUCK DETECTION] Failed to fix errors after " + maxRetries + " attempts. Please intervene.";
    }
}
