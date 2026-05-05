package com.opsvision.harness.service;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dynamic MCP service.
 *
 * Discovers tools from MCP servers via the protocol's `tools/list` and
 * executes them via `tools/call`. No LLM calls in this path.
 */
@Service
public class DynamicMcpService {

    private static final Logger log = LoggerFactory.getLogger(DynamicMcpService.class);
    private static final long TOOL_CACHE_TTL_MS = 5 * 60 * 1000L;

    @Autowired(required = false)
    private List<McpSyncClient> mcpClients = List.of();

    private Map<String, ToolDescriptor> availableTools = new LinkedHashMap<>();
    private Map<String, McpSyncClient> toolOwners = new HashMap<>();
    private long lastToolDiscovery = 0;

    public Map<String, ToolDescriptor> discoverAvailableTools() {
        long now = System.currentTimeMillis();
        if (now - lastToolDiscovery < TOOL_CACHE_TTL_MS && !availableTools.isEmpty()) {
            return availableTools;
        }

        if (mcpClients.isEmpty()) {
            log.warn("No MCP clients configured — tool discovery returns empty.");
            availableTools = new LinkedHashMap<>();
            toolOwners = new HashMap<>();
            lastToolDiscovery = now;
            return availableTools;
        }

        Map<String, ToolDescriptor> discovered = new LinkedHashMap<>();
        Map<String, McpSyncClient> owners = new HashMap<>();
        for (McpSyncClient client : mcpClients) {
            try {
                ListToolsResult result = client.listTools();
                for (Tool tool : result.tools()) {
                    String description = tool.description() != null ? tool.description() : "";
                    discovered.put(tool.name(), new ToolDescriptor(tool.name(), description));
                    owners.putIfAbsent(tool.name(), client);
                }
            } catch (Exception e) {
                log.error("listTools failed for MCP client {}: {}",
                          client.getServerInfo() != null ? client.getServerInfo().name() : "?",
                          e.getMessage(), e);
            }
        }

        availableTools = discovered;
        toolOwners = owners;
        lastToolDiscovery = now;
        log.info("Discovered {} tool(s) from {} MCP client(s): {}",
                 discovered.size(), mcpClients.size(),
                 discovered.keySet().stream().collect(Collectors.joining(", ")));
        return availableTools;
    }

    public String getToolDescriptionsForLLM() {
        Map<String, ToolDescriptor> tools = discoverAvailableTools();
        if (tools.isEmpty()) {
            return "No tools available";
        }
        StringBuilder sb = new StringBuilder("AVAILABLE MCP TOOLS:\n");
        for (ToolDescriptor t : tools.values()) {
            sb.append("- ").append(t.getName()).append(": ").append(t.getDescription()).append('\n');
        }
        return sb.toString();
    }

    public ToolExecutionResult executeTool(String toolName, Map<String, Object> parameters) {
        long started = System.currentTimeMillis();

        McpSyncClient client = clientForTool(toolName);
        if (client == null) {
            String msg = "Tool '" + toolName + "' not available on any configured MCP server";
            log.warn(msg);
            return new ToolExecutionResult(toolName, false, null, msg);
        }

        try {
            CallToolResult result = client.callTool(new CallToolRequest(toolName, parameters));
            String text = extractText(result);
            long latencyMs = System.currentTimeMillis() - started;
            boolean isError = Boolean.TRUE.equals(result.isError());
            log.info("MCP tool call: name={} latency_ms={} status={}",
                     toolName, latencyMs, isError ? "error" : "ok");
            return new ToolExecutionResult(toolName, !isError, text, isError ? text : null);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - started;
            log.error("MCP tool call: name={} latency_ms={} status=exception: {}",
                      toolName, latencyMs, e.getMessage(), e);
            return new ToolExecutionResult(toolName, false, null, e.getMessage());
        }
    }

    public List<ToolExecutionResult> executeSelectedTools(List<String> toolNames, String orderId) {
        Map<String, Object> params = new HashMap<>();
        if (orderId != null) {
            params.put("orderId", orderId);
        }
        log.info("Executing {} selected tool(s) with params {}", toolNames.size(), params);
        return toolNames.stream()
            .map(name -> executeTool(name, params))
            .collect(Collectors.toList());
    }

    public void invalidateToolCache() {
        log.info("Invalidating local MCP tool cache");
        availableTools = new LinkedHashMap<>();
        toolOwners = new HashMap<>();
        lastToolDiscovery = 0;
    }

    public void clearToolCache() {
        invalidateToolCache();
    }

    private McpSyncClient clientForTool(String toolName) {
        if (availableTools.isEmpty()) {
            discoverAvailableTools();
        }
        return toolOwners.get(toolName);
    }

    private static String extractText(CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Content c : result.content()) {
            if (c instanceof TextContent text) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(text.text());
            }
        }
        return sb.toString();
    }

    public static class ToolDescriptor {
        private final String name;
        private final String description;

        public ToolDescriptor(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }

        @Override
        public String toString() {
            return name + ": " + description;
        }
    }

    public static class ToolExecutionResult {
        private final String toolName;
        private final boolean success;
        private final String result;
        private final String error;

        public ToolExecutionResult(String toolName, boolean success, String result, String error) {
            this.toolName = toolName;
            this.success = success;
            this.result = result;
            this.error = error;
        }

        public String getToolName() { return toolName; }
        public boolean isSuccess() { return success; }
        public String getResult() { return result; }
        public String getError() { return error; }
    }
}
