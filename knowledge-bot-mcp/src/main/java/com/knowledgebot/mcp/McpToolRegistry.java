package com.knowledgebot.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    private final Map<String, McpToolDefinition> hostedTools = new ConcurrentHashMap<>();
    private final Map<String, McpToolDefinition> externalTools = new ConcurrentHashMap<>();

    public void registerHostedTool(String name, String description, List<McpToolParameter> params, String category) {
        McpToolDefinition tool = new McpToolDefinition(name, description, params, category);
        hostedTools.put(name, tool);
        log.info("MCP: Registered hosted tool: {} ({})", name, category);
    }

    public void registerExternalTool(String source, String name, String description, List<McpToolParameter> params, String category) {
        String key = source + "/" + name;
        McpToolDefinition tool = new McpToolDefinition(name, description, params, category);
        externalTools.put(key, tool);
        log.info("MCP: Registered external tool: {} ({})", key, category);
    }

    public List<McpToolDefinition> getAllTools() {
        List<McpToolDefinition> all = new ArrayList<>(hostedTools.values());
        all.addAll(externalTools.values());
        return all;
    }

    public List<McpToolDefinition> getToolsByCategory(String category) {
        return getAllTools().stream()
                .filter(t -> t.category().equalsIgnoreCase(category))
                .toList();
    }

    public McpToolDefinition getTool(String name) {
        return hostedTools.getOrDefault(name, externalTools.get(name));
    }

    public List<McpToolDefinition> getHostedTools() {
        return List.copyOf(hostedTools.values());
    }

    public List<McpToolDefinition> getExternalTools() {
        return List.copyOf(externalTools.values());
    }

    public int getHostedToolCount() {
        return hostedTools.size();
    }

    public int getExternalToolCount() {
        return externalTools.size();
    }

    public void initializeDefaultTools() {
        registerHostedTool("generate_plan", "Generate a multi-step execution plan",
                List.of(new McpToolParameter("goal", "string", "The goal to plan for", true)),
                "planning");

        registerHostedTool("hybrid_search", "Perform semantic and keyword search across indexed knowledge",
                List.of(new McpToolParameter("query", "string", "Search query", true),
                        new McpToolParameter("topK", "integer", "Number of results", false)),
                "retrieval");

        registerHostedTool("request_permission", "Request human approval for an action",
                List.of(new McpToolParameter("action", "string", "Action requiring approval", true)),
                "security");

        registerHostedTool("code_generate", "Generate code based on instructions",
                List.of(new McpToolParameter("instructions", "string", "Code generation instructions", true),
                        new McpToolParameter("language", "string", "Target programming language", false)),
                "development");

        registerHostedTool("code_review", "Review code changes and flag concerns",
                List.of(new McpToolParameter("diff", "string", "Unified diff to review", true)),
                "development");

        registerHostedTool("db_migrate", "Generate database migration SQL from JPA entities",
                List.of(new McpToolParameter("entity_file", "string", "Path to JPA entity file", true)),
                "database");

        registerHostedTool("code_modernize", "Modernize legacy Java code to latest idioms",
                List.of(new McpToolParameter("file_path", "string", "Path to Java file to modernize", true)),
                "development");

        registerHostedTool("workspace_scan", "Scan and index a project workspace",
                List.of(new McpToolParameter("path", "string", "Path to workspace directory", false)),
                "infrastructure");

        registerHostedTool("orchestrate", "Execute a plan with concurrent multi-agent parallel execution",
                List.of(new McpToolParameter("goal", "string", "Goal to orchestrate", true)),
                "orchestration");

        log.info("MCP: Initialized {} default hosted tools", hostedTools.size());
    }
}
