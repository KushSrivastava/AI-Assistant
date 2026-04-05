package com.knowledgebot.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Map<String, McpServerConnection> connections = new ConcurrentHashMap<>();

    public McpServerConnection connect(String serverUrl) {
        if (connections.containsKey(serverUrl)) {
            return connections.get(serverUrl);
        }

        log.info("MCP Client: Connecting to {}", serverUrl);
        McpServerConnection conn = new McpServerConnection(serverUrl, httpClient);
        connections.put(serverUrl, conn);
        return conn;
    }

    public void disconnect(String serverUrl) {
        McpServerConnection conn = connections.remove(serverUrl);
        if (conn != null) {
            log.info("MCP Client: Disconnected from {}", serverUrl);
        }
    }

    public List<McpServerConnection> getActiveConnections() {
        return List.copyOf(connections.values());
    }

    public String callTool(String serverUrl, String toolName, Map<String, Object> arguments) {
        McpServerConnection conn = connections.get(serverUrl);
        if (conn == null) {
            return "Error: Not connected to MCP server at " + serverUrl;
        }
        return conn.callTool(toolName, arguments);
    }

    public static class McpServerConnection {
        private final String serverUrl;
        private final HttpClient httpClient;
        private final List<McpToolDefinition> discoveredTools = new ArrayList<>();
        private boolean initialized = false;

        McpServerConnection(String serverUrl, HttpClient httpClient) {
            this.serverUrl = serverUrl;
            this.httpClient = httpClient;
        }

        public void initialize() {
            if (initialized) return;
            log.info("MCP: Initializing connection to {}", serverUrl);
            discoverTools();
            initialized = true;
        }

        public String callTool(String toolName, Map<String, Object> arguments) {
            try {
                String jsonPayload = """
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "method": "tools/call",
                        "params": {
                            "name": "%s",
                            "arguments": %s
                        }
                    }
                    """.formatted(toolName, mapToJson(arguments));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/mcp"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .timeout(Duration.ofSeconds(30))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.body();
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Error calling tool " + toolName + ": " + e.getMessage();
            }
        }

        public List<McpToolDefinition> getDiscoveredTools() {
            return List.copyOf(discoveredTools);
        }

        private void discoverTools() {
            try {
                String jsonPayload = """
                    {
                        "jsonrpc": "2.0",
                        "id": 0,
                        "method": "tools/list",
                        "params": {}
                    }
                    """;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/mcp"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.info("MCP: Discovered tools from {}: {}", serverUrl, response.body());
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("MCP: Failed to discover tools from {}: {}", serverUrl, e.getMessage());
            }
        }

        private String mapToJson(Map<String, Object> map) {
            if (map == null || map.isEmpty()) return "{}";
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                Object val = entry.getValue();
                if (val instanceof String) {
                    sb.append("\"").append(val).append("\"");
                } else {
                    sb.append(val);
                }
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
    }
}
