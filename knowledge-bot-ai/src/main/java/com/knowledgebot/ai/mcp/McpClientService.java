package com.knowledgebot.ai.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper; // <-- Jackson 3 Adapter
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper; // <-- The specific Jackson 3 Mapper it demands

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dynamic MCP (Model Context Protocol) connections.
 * Upgraded for Phase 4: Protocol-First Tooling with SSE and Stdio Transports.
 */
@Service
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

    // Cache of active Native MCP Clients keyed by a unique serverId
    private final Map<String, McpSyncClient> connections = new ConcurrentHashMap<>();

    public McpClientService() {
        // No dependency injection needed here anymore!
    }

    /**
     * Phase 4 - Step 1: SSE Transport (Web)
     * Connects to an external MCP Server over HTTP SSE (Server-Sent Events).
     */
    public McpSyncClient connectSse(String serverId, String url) {
        return connections.computeIfAbsent(serverId, id -> {
            log.info("MCP Client: Connecting to SSE server '{}' at {}", serverId, url);

            var transport = HttpClientSseClientTransport.builder(url).build();

            var client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(60))
                    .build();

            client.initialize();
            log.info("Successfully connected to MCP SSE Server '{}'. Discovered {} tools.",
                    serverId, client.listTools().tools().size());
            return client;
        });
    }

    /**
     * Phase 4 - Step 1: Stdio Transport (Local)
     * Spawns a local process and communicates via Standard IO.
     */
    public McpSyncClient connectStdio(String serverId, String command, List<String> args) {
        return connections.computeIfAbsent(serverId, id -> {
            log.info("MCP Client: Starting local Stdio server '{}' with command: {} {}", serverId, command, args);

            ServerParameters params = ServerParameters.builder(command)
                    .args(args)
                    .build();

            // FIXED: We build a completely isolated Jackson 3 JsonMapper specifically for the MCP SDK
            var mcpMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());
            var transport = new StdioClientTransport(params, mcpMapper);

            var client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(60))
                    .build();

            client.initialize();
            log.info("Successfully started Stdio MCP Server '{}'. Discovered {} tools.",
                    serverId, client.listTools().tools().size());
            return client;
        });
    }

    public void disconnect(String serverId) {
        McpSyncClient client = connections.remove(serverId);
        if (client != null) {
            client.closeGracefully();
            log.info("MCP Client: Disconnected from {}", serverId);
        }
    }

    /**
     * Phase 4 - Step 2: Dynamic Tool Registration helper.
     */
    public List<McpSyncClient> getActiveConnections() {
        return List.copyOf(connections.values());
    }

    public String callTool(String serverId, String toolName, Map<String, Object> arguments) {
        McpSyncClient client = connections.get(serverId);
        if (client == null) {
            return "Error: Not connected to MCP server: " + serverId;
        }

        try {
            var callResult = client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
            return callResult.content().toString();
        } catch (Exception e) {
            log.error("Failed to execute MCP tool: {}", toolName, e);
            return "Error calling tool " + toolName + ": " + e.getMessage();
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down all MCP connections gracefully...");
        connections.keySet().forEach(this::disconnect);
    }
}