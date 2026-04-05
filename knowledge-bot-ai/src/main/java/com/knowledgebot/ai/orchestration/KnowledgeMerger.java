package com.knowledgebot.ai.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KnowledgeMerger {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeMerger.class);

    private final ChatClient chatClient;

    public KnowledgeMerger(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String mergeResults(String goal, Map<String, String> taskResults) {
        if (taskResults.isEmpty()) {
            return "No results to merge.";
        }

        if (taskResults.size() == 1) {
            return taskResults.values().iterator().next();
        }

        String consolidatedResults = taskResults.entrySet().stream()
                .map(e -> "## " + e.getKey() + "\n\n" + e.getValue())
                .collect(Collectors.joining("\n\n---\n\n"));

        String mergePrompt = """
            You are a Technical Integration Specialist. Merge the following task results into a single, cohesive implementation summary.
            Remove redundancies, resolve conflicts, and ensure consistency across all sections.
            
            Original Goal: %s
            
            TASK RESULTS:
            %s
            
            Provide a unified, well-structured output that combines all results into a coherent whole.
            """.formatted(goal, consolidatedResults);

        log.info("Merging {} task results into unified output", taskResults.size());
        return chatClient.prompt().user(mergePrompt).call().content();
    }

    public String mergeCodeResults(Map<String, String> taskResults) {
        String codeBlocks = taskResults.entrySet().stream()
                .map(e -> "### " + e.getKey() + "\n" + e.getValue())
                .collect(Collectors.joining("\n\n"));

        String mergePrompt = """
            You are a Senior Software Engineer. Review the following code outputs from parallel tasks.
            Ensure they are compatible, follow consistent patterns, and can be integrated together.
            Flag any integration issues and provide the corrected unified code.
            
            PARALLEL CODE OUTPUTS:
            %s
            """.formatted(codeBlocks);

        return chatClient.prompt().user(mergePrompt).call().content();
    }
}
