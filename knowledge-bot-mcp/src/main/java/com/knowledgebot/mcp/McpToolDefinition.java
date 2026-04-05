package com.knowledgebot.mcp;

import java.util.List;
import java.util.Map;

public record McpToolDefinition(
    String name,
    String description,
    List<McpToolParameter> parameters,
    String category
) {
    public Map<String, Object> toMap() {
        return Map.of(
            "name", name,
            "description", description,
            "parameters", parameters.stream().map(McpToolParameter::toMap).toList(),
            "category", category
        );
    }
}
