package com.knowledgebot.ai.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

    // Cache of active Native MCP Clients
    private final Map<String, McpSyncClient> connections = new ConcurrentHashMap<>();

    /**
     * Connects to an external MCP Server using the official Java MCP SDK Transport.
     * This is optimized for Java 21+ Virtual Threads.
     */
    public McpSyncClient connect(String serverUrl) {
        return connections.computeIfAbsent(serverUrl, url -> {
            log.info("MCP Client: Connecting to {} using official HttpClientSseClientTransport", url);

            // Option 1: Official pure-Java SDK transport (No WebFlux needed!)
            var transport = HttpClientSseClientTransport.builder(url).build();

            var client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(30))
                    .build();

            client.initialize();
            return client;
        });
    }

    public void disconnect(String serverUrl) {
        McpSyncClient client = connections.remove(serverUrl);
        if (client != null) {
            client.closeGracefully();
            log.info("MCP Client: Disconnected from {}", serverUrl);
        }
    }

    public List<McpSyncClient> getActiveConnections() {
        return List.copyOf(connections.values());
    }

    /**
     * Fallback manual tool execution, though Spring AI's ChatClient
     * handles this automatically via .defaultToolCallbacks()
     */
    public String callTool(String serverUrl, String toolName, Map<String, Object> arguments) {
        McpSyncClient client = connections.get(serverUrl);
        if (client == null) {
            return "Error: Not connected to MCP server at " + serverUrl;
        }

        try {
            var callResult = client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
            return callResult.content().toString();
        } catch (Exception e) {
            log.error("Failed to execute MCP tool: {}", toolName, e);
            return "Error calling tool " + toolName + ": " + e.getMessage();
        }
    }
}