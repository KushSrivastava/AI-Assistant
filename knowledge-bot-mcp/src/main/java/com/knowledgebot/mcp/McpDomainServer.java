package com.knowledgebot.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class McpDomainServer {

    private static final Logger log = LoggerFactory.getLogger(McpDomainServer.class);

    private final McpClientService mcpClientService;
    private final McpToolRegistry toolRegistry;

    public McpDomainServer(McpClientService mcpClientService, McpToolRegistry toolRegistry) {
        this.mcpClientService = mcpClientService;
        this.toolRegistry = toolRegistry;
    }

    public void connectFinancialServer(String url) {
        log.info("Connecting to Financial MCP server: {}", url);
        var conn = mcpClientService.connect(url);
        conn.initialize();
        log.info("Financial MCP connected. Available tools: {}", conn.getDiscoveredTools().size());
    }

    public void connectWebScrapingServer(String url) {
        log.info("Connecting to Web Scraping MCP server: {}", url);
        var conn = mcpClientService.connect(url);
        conn.initialize();
        log.info("Web Scraping MCP connected. Available tools: {}", conn.getDiscoveredTools().size());
    }

    public void connectTravelServer(String url) {
        log.info("Connecting to Travel MCP server: {}", url);
        var conn = mcpClientService.connect(url);
        conn.initialize();
        log.info("Travel MCP connected. Available tools: {}", conn.getDiscoveredTools().size());
    }

    public void connectResearchServer(String url) {
        log.info("Connecting to Research MCP server: {}", url);
        var conn = mcpClientService.connect(url);
        conn.initialize();
        log.info("Research MCP connected. Available tools: {}", conn.getDiscoveredTools().size());
    }

    public String callFinancialTool(String toolName, Map<String, Object> args) {
        return callToolFromCategory("financial", toolName, args);
    }

    public String callWebScrapingTool(String toolName, Map<String, Object> args) {
        return callToolFromCategory("webscraping", toolName, args);
    }

    public String callTravelTool(String toolName, Map<String, Object> args) {
        return callToolFromCategory("travel", toolName, args);
    }

    public String callResearchTool(String toolName, Map<String, Object> args) {
        return callToolFromCategory("research", toolName, args);
    }

    private String callToolFromCategory(String category, String toolName, Map<String, Object> args) {
        var connections = mcpClientService.getActiveConnections();
        for (var conn : connections) {
            log.info("Calling tool {} via MCP connection to {}", toolName, conn);
            return conn.callTool(toolName, args);
        }
        return "Error: No MCP server connected for category: " + category;
    }

    public String getDomainStatus() {
        var connections = mcpClientService.getActiveConnections();
        StringBuilder sb = new StringBuilder("=== MCP Domain Servers ===\n");
        sb.append("Hosted tools: ").append(toolRegistry.getHostedToolCount()).append("\n");
        sb.append("External tools: ").append(toolRegistry.getExternalToolCount()).append("\n");
        sb.append("Active connections: ").append(connections.size()).append("\n");
        for (var conn : connections) {
            sb.append("  - ").append(conn).append("\n");
        }
        return sb.toString();
    }
}
