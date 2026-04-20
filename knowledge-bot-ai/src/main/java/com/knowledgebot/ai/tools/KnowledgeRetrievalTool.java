package com.knowledgebot.ai.tools;

import com.knowledgebot.ai.knowledge.KnowledgeIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Exposes the personal knowledge base to the agentic loop.
 * The LLM can proactively search the knowledge repository (e.g. documentation,
 * guidelines, sample code) that the user has provided.
 */
@Component
public class KnowledgeRetrievalTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalTool.class);
    private final KnowledgeIngestionService knowledgeIngestionService;

    public KnowledgeRetrievalTool(KnowledgeIngestionService knowledgeIngestionService) {
        this.knowledgeIngestionService = knowledgeIngestionService;
    }

    @Tool(description = """
        Query the user's personal knowledge base. Use this to lookup documentation, 
        coding guidelines, architectural decisions, and other reference materials 
        that the user has explicitly provided to you. Returns useful snippets.
        """)
    public String queryKnowledge(
        @ToolParam(description = "The query or topic you need to learn about, e.g. 'Authentication rules' or 'React hook examples'")
        String query
    ) {
        log.info("Agent queried knowledge base for: {}", query);
        List<Document> results = knowledgeIngestionService.queryKnowledge(query, 5);

        if (results.isEmpty()) {
            return "No relevant information found in the knowledge base for: '" + query + "'.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size()).append(" relevant snippets in the knowledge base:\n\n");
        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            String filename = (String) doc.getMetadata().getOrDefault("filename", "unknown source");
            sb.append("--- Snippet ").append(i + 1).append(" (from ").append(filename).append(") ---\n");
            sb.append(doc.getText()).append("\n\n");
        }
        
        return sb.toString();
    }
}
