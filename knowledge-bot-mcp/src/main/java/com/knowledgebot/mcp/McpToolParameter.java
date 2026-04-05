package com.knowledgebot.mcp;

import java.util.Map;

public record McpToolParameter(
    String name,
    String type,
    String description,
    boolean required
) {
    public Map<String, Object> toMap() {
        return Map.of(
            "name", name,
            "type", type,
            "description", description,
            "required", required
        );
    }
}
