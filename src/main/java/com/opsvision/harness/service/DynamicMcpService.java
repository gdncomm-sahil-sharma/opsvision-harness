package com.opsvision.harness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool discovery + cache for the SimpleChatController introspection
 * endpoints (/api/chat/tools, /api/chat/tools/refresh) and the
 * /api/v1/health probe. Tool *invocation* is handled by Spring AI's
 * ToolCallback machinery in AIAssistantService — this service only
 * surfaces the catalog.
 */
@Service
public class DynamicMcpService {

    private static final Logger log = LoggerFactory.getLogger(DynamicMcpService.class);

    @Autowired(required = false)
    private SyncMcpToolCallbackProvider mcpToolProvider;

    @Autowired(required = false)
    private McpSyncClient mcpClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, ToolDescriptor> availableTools = new HashMap<>();
    private long lastToolDiscovery = 0;
    private static final long TOOL_CACHE_TTL = 300_000; // 5 minutes

    public Map<String, ToolDescriptor> discoverAvailableTools() {
        long now = System.currentTimeMillis();
        if (now - lastToolDiscovery < TOOL_CACHE_TTL && !availableTools.isEmpty()) {
            log.debug("Using cached tool discovery from {} ms ago", now - lastToolDiscovery);
            return availableTools;
        }

        log.info("Discovering tools from MCP server via tools/list...");
        Map<String, ToolDescriptor> discovered = discoverToolsFromMcpServer();
        availableTools = discovered;
        lastToolDiscovery = now;

        if (discovered.isEmpty()) {
            log.warn("No MCP tools discovered. /api/chat will have no tools to call.");
        } else {
            log.info("Discovered {} tool(s): {}",
                    discovered.size(),
                    discovered.keySet().stream().collect(Collectors.joining(", ")));
        }
        return discovered;
    }

    public void invalidateToolCache() {
        log.info("Invalidating tool cache; next request will rediscover");
        availableTools.clear();
        lastToolDiscovery = 0;
        if (mcpToolProvider != null) {
            try {
                mcpToolProvider.invalidateCache();
            } catch (Exception e) {
                log.debug("Could not invalidate MCP provider cache: {}", e.getMessage());
            }
        }
    }

    public void clearToolCache() {
        invalidateToolCache();
    }

    private Map<String, ToolDescriptor> discoverToolsFromMcpServer() {
        Map<String, ToolDescriptor> tools = new HashMap<>();
        try {
            ToolCallback[] callbacks = resolveCallbacks();
            if (callbacks == null || callbacks.length == 0) {
                log.warn("No MCP client or provider available for tool discovery");
                return tools;
            }
            for (ToolCallback callback : callbacks) {
                try {
                    String name = getToolName(callback);
                    String description = getFullToolDefinition(callback);
                    if (name != null) {
                        tools.put(name, new ToolDescriptor(name, description));
                        log.debug("Discovered '{}' ({} char description)", name, description.length());
                    }
                } catch (Exception e) {
                    log.warn("Failed to read tool callback metadata: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error discovering tools from MCP server: {}", e.getMessage(), e);
        }
        return tools;
    }

    private ToolCallback[] resolveCallbacks() {
        if (mcpClient != null) {
            log.debug("Using McpSyncClient for tool discovery");
            return new SyncMcpToolCallbackProvider(mcpClient).getToolCallbacks();
        }
        if (mcpToolProvider != null) {
            log.debug("Using autoconfigured SyncMcpToolCallbackProvider for tool discovery");
            return mcpToolProvider.getToolCallbacks();
        }
        return new ToolCallback[0];
    }

    private String getToolName(ToolCallback callback) {
        try {
            for (String methodName : new String[]{"getName", "name", "getToolName", "toolName"}) {
                try {
                    Method method = callback.getClass().getMethod(methodName);
                    Object result = method.invoke(callback);
                    if (result != null) {
                        return result.toString();
                    }
                } catch (NoSuchMethodException ignored) {
                    // try next candidate
                }
            }
            Object toolDefinition = callback.getToolDefinition();
            if (toolDefinition != null) {
                String defStr = toolDefinition.toString();
                if (defStr.contains("name=") || defStr.contains("\"name\"")) {
                    String extracted = extractNameFromDefinition(defStr);
                    if (!"unknown_tool".equals(extracted)) {
                        return extracted;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error getting tool name: {}", e.getMessage());
        }
        return "unknown_tool_" + System.currentTimeMillis();
    }

    private String getFullToolDefinition(ToolCallback callback) {
        try {
            String toolName = getToolName(callback);
            Object toolDefinition = callback.getToolDefinition();
            if (toolDefinition == null) {
                return "MCP tool: " + toolName;
            }
            String defStr = toolDefinition.toString();
            String description = extractDescriptionFromToolDefinition(defStr);
            return (description != null && description.length() > 10) ? description : defStr;
        } catch (Exception e) {
            log.debug("Error getting tool definition: {}", e.getMessage());
            return "MCP tool";
        }
    }

    private String extractNameFromDefinition(String definitionStr) {
        try {
            int nameIndex = definitionStr.indexOf("name=");
            if (nameIndex >= 0) {
                int valueStart = nameIndex + 5;
                int valueEnd = definitionStr.indexOf(",", valueStart);
                if (valueEnd < 0) valueEnd = definitionStr.indexOf("]", valueStart);
                if (valueEnd < 0) valueEnd = definitionStr.indexOf(" ", valueStart);
                if (valueEnd > valueStart) {
                    String name = definitionStr.substring(valueStart, valueEnd).trim();
                    if (!name.isEmpty()) {
                        return name;
                    }
                }
            }
            int jsonNameIndex = definitionStr.indexOf("\"name\"");
            if (jsonNameIndex >= 0) {
                int valueStart = definitionStr.indexOf(":", jsonNameIndex) + 1;
                valueStart = definitionStr.indexOf("\"", valueStart) + 1;
                int valueEnd = definitionStr.indexOf("\"", valueStart);
                if (valueStart > 0 && valueEnd > valueStart) {
                    String name = definitionStr.substring(valueStart, valueEnd).trim();
                    if (!name.isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting name from definition: {}", e.getMessage());
        }
        return "unknown_tool";
    }

    private String extractDescriptionFromToolDefinition(String definitionStr) {
        try {
            if (definitionStr.contains("DefaultToolDefinition[") && definitionStr.contains("description=")) {
                int descStart = definitionStr.indexOf("description=");
                if (descStart >= 0) {
                    int valueStart = descStart + 12;
                    int valueEnd = definitionStr.lastIndexOf("]");
                    if (valueEnd < 0) valueEnd = definitionStr.length();
                    if (valueStart < valueEnd) {
                        String desc = definitionStr.substring(valueStart, valueEnd).trim();
                        if (!desc.isEmpty()) {
                            return desc;
                        }
                    }
                }
            }
            if (definitionStr.contains("\"description\"")) {
                try {
                    JsonNode node = objectMapper.readTree(definitionStr);
                    if (node.has("description")) {
                        String desc = node.get("description").asText();
                        if (desc != null && !desc.trim().isEmpty()) {
                            return desc.trim();
                        }
                    }
                } catch (Exception ignored) {
                    // fall through to text extraction
                }
                int descStart = definitionStr.indexOf("\"description\"");
                if (descStart >= 0) {
                    int valueStart = definitionStr.indexOf(":", descStart) + 1;
                    int valueEnd = findDescriptionEnd(definitionStr, valueStart);
                    if (valueStart > 0 && valueEnd > valueStart) {
                        String desc = definitionStr.substring(valueStart, valueEnd)
                                .trim()
                                .replaceAll("^\"|\"$", "")
                                .replaceAll("\\\\n", "\n")
                                .replaceAll("\\\\\"", "\"");
                        if (!desc.isEmpty()) {
                            return desc;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting description: {}", e.getMessage());
        }
        return null;
    }

    private int findDescriptionEnd(String text, int start) {
        try {
            while (start < text.length() && (Character.isWhitespace(text.charAt(start)) || text.charAt(start) == '"')) {
                start++;
            }
            int pos = start;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == '"' && (pos == start || text.charAt(pos - 1) != '\\')) {
                    return pos;
                }
                if (c == ',' || c == '}' || c == '\n') {
                    return pos;
                }
                pos++;
            }
            return text.length();
        } catch (Exception e) {
            return text.length();
        }
    }

    public static class ToolDescriptor {
        private final String name;
        private final String description;

        public ToolDescriptor(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return String.format("%s: %s", name, description);
        }
    }
}
