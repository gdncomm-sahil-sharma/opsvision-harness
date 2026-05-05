package com.opsvision.harness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🔍 Dynamic MCP Service
 * 
 * Discovers available tools from MCP server at runtime.
 * No hardcoded tool knowledge - server tells us everything!
 */
@Service
public class DynamicMcpService {
    
    private static final Logger log = LoggerFactory.getLogger(DynamicMcpService.class);
    
    @Autowired
    private ChatClient chatClient;
    
    @Autowired
    private ChatModel chatModel;
    
    @Autowired(required = false)
    private SyncMcpToolCallbackProvider mcpToolProvider;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, ToolDescriptor> availableTools = new HashMap<>();
    private long lastToolDiscovery = 0;
    private static final long TOOL_CACHE_TTL = 300000; // 5 minutes
    
    /**
     * 🔎 Discover all available tools from MCP server using ChatClient with MCP integration
     */
    public Map<String, ToolDescriptor> discoverAvailableTools() {
        long currentTime = System.currentTimeMillis();
        
        // Use cached tools if recent
        if (currentTime - lastToolDiscovery < TOOL_CACHE_TTL && !availableTools.isEmpty()) {
            log.debug("Using cached tool discovery from {} ms ago", currentTime - lastToolDiscovery);
            return availableTools;
        }
        
        log.info("Discovering available tools from MCP server using ChatClient with MCP integration...");
        
        try {
            // Create ChatClient with MCP tools enabled
            ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);
            
            if (mcpToolProvider != null) {
                chatClientBuilder.defaultToolCallbacks(mcpToolProvider);
            }
            
            ChatClient mcpEnabledClient = chatClientBuilder.build();
            
            // Ask the LLM about available functions/tools
            String toolDiscoveryPrompt = """
                List all the functions/tools that are available to you right now.
                For each function, provide its name and what it does.
                Format your response as JSON with this structure:
                [
                  {"name": "function_name", "description": "what it does"},
                  {"name": "another_function", "description": "what it does"}
                ]
                
                Only list actual functions you can call, not general capabilities.
                """;
                
            var response = mcpEnabledClient.prompt()
                .user(toolDiscoveryPrompt)
                .call()
                .content();
            
            // Parse the response to extract tool information
            Map<String, ToolDescriptor> discoveredTools = parseToolsFromLLMResponse(response);
            
            // If no tools discovered via LLM, try fallback discovery
            if (discoveredTools.isEmpty()) {
                log.warn("No tools discovered via LLM response, trying fallback method");
                discoveredTools = discoverToolsViaTesting();
            }
            
            availableTools = discoveredTools;
            lastToolDiscovery = currentTime;
            
            log.info("Successfully discovered {} tools from MCP server: {}", 
                     availableTools.size(), 
                     availableTools.keySet().stream().collect(Collectors.joining(", ")));
            
            return availableTools;
            
        } catch (Exception e) {
            log.error("Failed to discover tools from MCP server: {}", e.getMessage(), e);
            
            // Fallback to known tools if discovery fails
            return getFallbackTools();
        }
    }
    
    /**
     * 🤖 Get tool descriptions for LLM context
     */
    public String getToolDescriptionsForLLM() {
        Map<String, ToolDescriptor> tools = discoverAvailableTools();
        
        if (tools.isEmpty()) {
            return "No tools available";
        }
        
        StringBuilder descriptions = new StringBuilder();
        descriptions.append("AVAILABLE MCP TOOLS:\n");
        
        for (Map.Entry<String, ToolDescriptor> entry : tools.entrySet()) {
            ToolDescriptor tool = entry.getValue();
            descriptions.append(String.format("- %s: %s\n", 
                                             tool.getName(), 
                                             tool.getDescription()));
        }
        
        return descriptions.toString();
    }
    
    /**
     * 🔧 Execute tool by name with parameters using ChatClient with MCP tools
     */
    public ToolExecutionResult executeTool(String toolName, Map<String, Object> parameters) {
        log.info("Executing tool '{}' with parameters: {}", toolName, parameters);
        
        try {
            // Create ChatClient with MCP tools enabled
            ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);
            
            if (mcpToolProvider != null) {
                chatClientBuilder.defaultToolCallbacks(mcpToolProvider);
            }
            
            ChatClient mcpEnabledClient = chatClientBuilder.build();
            
            // Create a specific prompt to trigger the tool execution
            String toolExecutionPrompt = buildToolExecutionPrompt(toolName, parameters);
            
            var response = mcpEnabledClient.prompt()
                .user(toolExecutionPrompt)
                .call()
                .content();
            
            return new ToolExecutionResult(toolName, true, response, null);
            
        } catch (Exception e) {
            log.error("Failed to execute tool '{}': {}", toolName, e.getMessage(), e);
            return new ToolExecutionResult(toolName, false, null, e.getMessage());
        }
    }
    
    /**
     * 🔧 Execute multiple tools based on LLM selection
     */
    public List<ToolExecutionResult> executeSelectedTools(List<String> toolNames, String orderId) {
        log.info("Executing {} selected tools for context: {}", toolNames.size(), orderId);
        
        return toolNames.stream()
            .map(toolName -> {
                Map<String, Object> params = Map.of("orderId", orderId);
                return executeTool(toolName, params);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Parse tools from LLM response about available functions
     */
    private Map<String, ToolDescriptor> parseToolsFromLLMResponse(String response) {
        Map<String, ToolDescriptor> tools = new HashMap<>();
        
        try {
            // Try to parse JSON response
            JsonNode rootNode = objectMapper.readTree(response);
            
            if (rootNode.isArray()) {
                for (JsonNode toolNode : rootNode) {
                    if (toolNode.has("name")) {
                        String name = toolNode.get("name").asText();
                        String description = toolNode.has("description") ? 
                            toolNode.get("description").asText() : "Available function";
                        tools.put(name, new ToolDescriptor(name, description));
                    }
                }
            }
            
        } catch (Exception e) {
            log.debug("Failed to parse JSON response, trying text parsing: {}", e.getMessage());
            
            // Fallback: extract tool names from text response
            tools = parseToolsFromText(response);
        }
        
        return tools;
    }
    
    /**
     * Parse tools from text response
     */
    private Map<String, ToolDescriptor> parseToolsFromText(String response) {
        Map<String, ToolDescriptor> tools = new HashMap<>();
        
        // Look for function patterns in the response
        String[] lines = response.split("\n");
        for (String line : lines) {
            // Look for patterns like "functionName" or "get_something"
            if (line.toLowerCase().contains("function") || 
                line.matches(".*\\b(get|post|put|delete)_\\w+.*") ||
                line.matches(".*\\b\\w+Timeline\\b.*") ||
                line.matches(".*\\b\\w+History\\b.*") ||
                line.matches(".*\\b\\w+Events\\b.*")) {
                
                String[] parts = line.split("[:\\-]", 2);
                if (parts.length >= 1) {
                    String name = parts[0].trim()
                        .replaceAll("^[-*•]\\s*", "")
                        .replaceAll("\"", "")
                        .replaceAll("\\(.*\\)", "");
                    
                    String description = parts.length > 1 ? parts[1].trim() : "Available function";
                    
                    if (name.length() > 2 && !name.toLowerCase().contains("function")) {
                        tools.put(name, new ToolDescriptor(name, description));
                    }
                }
            }
        }
        
        return tools;
    }
    
    /**
     * Discover tools by letting LLM list what's available (no hardcoded testing)
     */
    private Map<String, ToolDescriptor> discoverToolsViaTesting() {
        Map<String, ToolDescriptor> tools = new HashMap<>();
        
        try {
            ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);
            if (mcpToolProvider != null) {
                chatClientBuilder.defaultToolCallbacks(mcpToolProvider);
            }
            ChatClient testClient = chatClientBuilder.build();
            
            // Ask LLM to introspect its available functions without hardcoding names
            String introspectionPrompt = """
                Without executing anything, just tell me what functions or tools you have access to right now.
                List each function name you can actually call.
                Don't make up functions - only list what you actually have available.
                Format: just the function names, one per line.
                """;
                
            var response = testClient.prompt()
                .user(introspectionPrompt)
                .call()
                .content();
            
            // Parse function names from response
            String[] lines = response.split("\n");
            for (String line : lines) {
                String cleanLine = line.trim()
                    .replaceAll("^[-*•]\\s*", "")
                    .replaceAll("[\\(\\)\\[\\]]", "")
                    .trim();
                    
                if (cleanLine.length() > 2 && 
                    !cleanLine.toLowerCase().contains("function") &&
                    !cleanLine.toLowerCase().contains("available") &&
                    !cleanLine.toLowerCase().contains("tool")) {
                    
                    String description = generateDescriptionFromName(cleanLine);
                    tools.put(cleanLine, new ToolDescriptor(cleanLine, description));
                    log.debug("Discovered function via introspection: {}", cleanLine);
                }
            }
            
        } catch (Exception e) {
            log.warn("Function introspection failed: {}", e.getMessage());
        }
        
        return tools;
    }
    
    /**
     * Generate description from function name (generic, no hardcoded patterns)
     */
    private String generateDescriptionFromName(String functionName) {
        String name = functionName.toLowerCase();
        
        // Generic pattern-based description generation
        if (name.contains("get") || name.contains("fetch") || name.contains("retrieve")) {
            if (name.contains("timeline")) return "Get timeline and lifecycle events";
            if (name.contains("history")) return "Get historical data and records";
            if (name.contains("audit")) return "Get audit trail and compliance information";
            if (name.contains("task")) return "Get task execution information";
            if (name.contains("inventory")) return "Get inventory and stock information";
            if (name.contains("order")) return "Get order-related information";
            return "Get data from " + functionName.replaceAll("get|fetch|retrieve", "").trim();
        }
        
        if (name.contains("list") || name.contains("find") || name.contains("search")) {
            return "List or search for information";
        }
        
        if (name.contains("create") || name.contains("add") || name.contains("insert")) {
            return "Create or add new data";
        }
        
        if (name.contains("update") || name.contains("modify") || name.contains("edit")) {
            return "Update or modify existing data";
        }
        
        // Default fallback
        return "Available function: " + functionName;
    }
    
    /**
     * Build specific prompt to trigger tool execution
     */
    private String buildToolExecutionPrompt(String toolName, Map<String, Object> parameters) {
        StringBuilder prompt = new StringBuilder();
        
        // Create a prompt that will likely trigger the specific function
        String orderId = (String) parameters.get("orderId");
        
        String functionCall = String.format("Call the %s function for order %s", toolName, orderId);
        
        // Add context to make the function call more likely to be triggered
        prompt.append("I need warehouse data for order ").append(orderId).append(". ");
        prompt.append(functionCall).append(". ");
        
        if (toolName.toLowerCase().contains("timeline")) {
            prompt.append("Show me the complete order timeline and lifecycle events.");
        } else if (toolName.toLowerCase().contains("task")) {
            prompt.append("Show me the warehouse task execution history and status.");
        } else if (toolName.toLowerCase().contains("audit")) {
            prompt.append("Show me the audit trail and compliance events.");
        } else if (toolName.toLowerCase().contains("inventory")) {
            prompt.append("Show me the inventory movements and location history.");
        } else {
            prompt.append("Execute the function and return the results.");
        }
        
        return prompt.toString();
    }
    
    /**
     * Fallback - return empty map if discovery fails (no hardcoded tools)
     */
    private Map<String, ToolDescriptor> getFallbackTools() {
        log.warn("Tool discovery failed completely - no tools available");
        return new HashMap<>();
    }
    
    /**
     * Clear tool cache to force re-discovery
     */
    public void invalidateToolCache() {
        log.info("Invalidating tool cache, forcing fresh discovery on next request");
        availableTools.clear();
        lastToolDiscovery = 0;
        
        // Also invalidate MCP provider cache if available
        if (mcpToolProvider != null) {
            try {
                mcpToolProvider.invalidateCache();
            } catch (Exception e) {
                log.debug("Could not invalidate MCP provider cache: {}", e.getMessage());
            }
        }
    }
    
    // Data classes
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
            return String.format("%s: %s", name, description);
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