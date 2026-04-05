package com.knowledgebot.ai.chunking;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CodeChunkingService {
    
    // Chunking files by roughly 800 tokens max with 100 overlap tokens to preserve code context logic
    private final TokenTextSplitter splitter = new TokenTextSplitter(800, 350, 5, 10000, true);
    
    public List<Document> chunk(Document document) {
        return splitter.apply(List.of(document));
    }

    public List<Document> chunk(List<Document> documents) {
        return splitter.apply(documents);
    }
}
