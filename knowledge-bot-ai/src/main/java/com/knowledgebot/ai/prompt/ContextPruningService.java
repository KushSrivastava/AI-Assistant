package com.knowledgebot.ai.prompt;

import com.knowledgebot.core.performance.TokenBudgetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Logic-adapted from the user's recommendations: Implements Context Pruning.
 * Intelligently manages the context window by summarizing old "thoughts" instead of simple truncation.
 */
@Service
public class ContextPruningService {
    private static final Logger log = LoggerFactory.getLogger(ContextPruningService.class);
    private final TokenBudgetService tokenBudgetService;
    private final ChatClient chatClient;

    public ContextPruningService(TokenBudgetService tokenBudgetService, ChatClient.Builder chatClientBuilder) {
        this.tokenBudgetService = tokenBudgetService;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Prunes or summarizes the conversation history to fit within the token budget.
     * @param history List of conversation messages/thoughts
     * @param budget Max tokens allowed
     * @return Pruned/Summarized history
     */
    public List<String> prune(List<String> history, int budget) {
        long currentTokens = tokenBudgetService.estimateTokens(String.join("\n", history));
        
        if (currentTokens <= budget) {
            return history;
        }

        log.info("Context budget exceeded ({} > {}). Starting intelligent pruning/summarization.", currentTokens, budget);
        
        // Strategy: Keep the most recent 3 entries as-is, summarize everything older.
        int keepRecent = 3;
        if (history.size() <= keepRecent) {
            return history; // Cannot prune further without losing everything
        }

        List<String> olderHistory = history.subList(0, history.size() - keepRecent);
        List<String> recentHistory = history.subList(history.size() - keepRecent, history.size());

        String summary = summarizeHistory(olderHistory);
        
        List<String> result = new ArrayList<>();
        result.add("[SYSTEM SUMMARY of previous context]: " + summary);
        result.addAll(recentHistory);
        
        return result;
    }

    private String summarizeHistory(List<String> olderHistory) {
        String contentToSummarize = String.join("\n", olderHistory);
        log.debug("Summarizing {} characters of old context.", contentToSummarize.length());

        return chatClient.prompt()
                .system("Summarize the following conversation history into a concise, high-density paragraph. " +
                        "Retain key decisions, file paths, and current goals.")
                .user(contentToSummarize)
                .call()
                .content();
    }
}
