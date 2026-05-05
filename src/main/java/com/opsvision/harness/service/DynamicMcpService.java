package com.opsvision.harness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.modelcontextprotocol.client.McpSyncClient;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    
    @Autowired(required = false)
    private McpSyncClient mcpClient;
    
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
            
            // First try to get tools directly from MCP server using proper protocol
            Map<String, ToolDescriptor> discoveredTools = discoverToolsFromMcpServer();
            
            // If MCP server discovery fails, parse LLM response as fallback
            if (discoveredTools.isEmpty()) {
                log.warn("No tools discovered from MCP server, trying LLM response parsing");
                discoveredTools = parseToolsFromLLMResponse(response);
            }
            
            // If still no tools, try fallback discovery
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
     * 🔧 Execute tool by name with parameters using ChatClient with MCP tools (with session retry)
     */
    public ToolExecutionResult executeTool(String toolName, Map<String, Object> parameters) {
        log.info("Executing tool '{}' with parameters: {}", toolName, parameters);
        
        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
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
                
                log.debug("Tool '{}' executed successfully on attempt {}", toolName, attempt);
                return new ToolExecutionResult(toolName, true, response, null);
                
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                boolean isSessionError = errorMsg != null && 
                    (errorMsg.contains("session") || errorMsg.contains("Session") || 
                     errorMsg.contains("terminated") || errorMsg.contains("not found") ||
                     errorMsg.contains("MCP session"));
                
                if (isSessionError && attempt < maxRetries) {
                    log.warn("MCP session issue on attempt {} for tool '{}', retrying... Error: {}", 
                        attempt, toolName, errorMsg);
                    
                    // Clear tool cache to force session refresh
                    invalidateToolCache();
                    
                    // Wait before retry with progressive backoff
                    try {
                        Thread.sleep(1000 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                
                log.error("Tool '{}' failed after {} attempts. Error: {}", toolName, attempt, errorMsg, e);
                return new ToolExecutionResult(toolName, false, null, errorMsg);
            }
        }
        
        return new ToolExecutionResult(toolName, false, null, "Max retries exceeded");
    }
    
    /**
     * 🔧 Execute multiple tools based on LLM selection with intelligent parameter extraction
     */
    public List<ToolExecutionResult> executeSelectedTools(List<String> toolNames, String userMessage) {
        log.info("Executing {} selected tools for user message: '{}'", toolNames.size(), userMessage);
        
        // Extract smart parameters from user message
        Map<String, Object> smartParameters = extractSmartParameters(userMessage, toolNames);
        
        return toolNames.stream()
            .map(toolName -> {
                log.info("Executing tool '{}' with extracted parameters: {}", toolName, smartParameters);
                return executeTool(toolName, smartParameters);
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
     * 🔗 Discover tools directly from MCP client using tool definitions
     * Based on: var provider = new SyncMcpToolCallbackProvider(mcpClient);
     *           ToolCallback[] tools = provider.getToolCallbacks();
     *           tools[i].getToolDefinition()
     */
    private Map<String, ToolDescriptor> discoverToolsFromMcpServer() {
        Map<String, ToolDescriptor> tools = new HashMap<>();
        
        try {
            log.info("Discovering tools from MCP server using direct tool definition access...");
            
            // Method 1: Use McpSyncClient directly (as suggested by user)
            if (mcpClient != null) {
                log.debug("Using McpSyncClient for tool discovery");
                var provider = new SyncMcpToolCallbackProvider(mcpClient);
                ToolCallback[] toolCallbacks = provider.getToolCallbacks();
                
                if (toolCallbacks != null && toolCallbacks.length > 0) {
                    log.info("Found {} MCP tool callbacks via McpSyncClient", toolCallbacks.length);
                    
                    for (ToolCallback callback : toolCallbacks) {
                        try {
                            String toolName = getToolName(callback);
                            String fullDescription = getFullToolDefinition(callback);
                            
                            if (toolName != null) {
                                tools.put(toolName, new ToolDescriptor(toolName, fullDescription));
                                log.debug("Discovered '{}' with {} char description", toolName, fullDescription.length());
                            }
                            
                        } catch (Exception e) {
                            log.warn("Failed to get tool definition for callback: {}", e.getMessage());
                        }
                    }
                    
                    log.info("Successfully discovered {} tools with full descriptions via McpSyncClient", tools.size());
                    return tools;
                }
            }
            
            // Method 2: Fallback to existing provider
            if (mcpToolProvider != null) {
                log.debug("Using existing SyncMcpToolCallbackProvider for tool discovery");
                ToolCallback[] toolCallbacks = mcpToolProvider.getToolCallbacks();
                
                if (toolCallbacks != null && toolCallbacks.length > 0) {
                    log.info("Found {} MCP tool callbacks from provider", toolCallbacks.length);
                    
                    for (ToolCallback callback : toolCallbacks) {
                        try {
                            String toolName = getToolName(callback);
                            String fullDescription = getFullToolDefinition(callback);
                            
                            if (toolName != null) {
                                tools.put(toolName, new ToolDescriptor(toolName, fullDescription));
                                log.debug("Discovered '{}' with {} char description", toolName, fullDescription.length());
                            }
                            
                        } catch (Exception e) {
                            log.warn("Failed to get tool definition for callback: {}", e.getMessage());
                        }
                    }
                    
                    log.info("Successfully discovered {} tools via fallback provider", tools.size());
                    return tools;
                }
            }
            
            log.warn("No MCP client or provider available for tool discovery");
            
        } catch (Exception e) {
            log.error("Error discovering tools from MCP server: {}", e.getMessage(), e);
        }
        
        return tools;
    }
    
    /**
     * Get tool name using reflection (ToolCallback doesn't have getName())
     */
    private String getToolName(ToolCallback callback) {
        try {
            // Try different possible method names for getting tool name
            String[] possibleMethods = {"getName", "name", "getToolName", "toolName"};
            
            for (String methodName : possibleMethods) {
                try {
                    Method method = callback.getClass().getMethod(methodName);
                    Object result = method.invoke(callback);
                    if (result != null) {
                        return result.toString();
                    }
                } catch (NoSuchMethodException e) {
                    // Try next method
                }
            }
            
            // If no name method found, try to extract from tool definition
            Object toolDefinition = callback.getToolDefinition();
            if (toolDefinition != null) {
                String defStr = toolDefinition.toString();
                log.debug("🔍 Trying to extract name from tool definition: {}", defStr.length() > 200 ? defStr.substring(0, 200) + "..." : defStr);
                
                // Look for name in the definition string
                if (defStr.contains("name=") || defStr.contains("\"name\"")) {
                    String extractedName = extractNameFromDefinition(defStr);
                    if (!"unknown_tool".equals(extractedName)) {
                        return extractedName;
                    }
                }
            }
            
        } catch (Exception e) {
            log.debug("Error getting tool name: {}", e.getMessage());
        }
        
        return "unknown_tool_" + System.currentTimeMillis();
    }
    
    /**
     * Get full tool definition using the callback's getToolDefinition() method
     * This is the key approach suggested by the user: callback.getToolDefinition()
     */
    private String getFullToolDefinition(ToolCallback callback) {
        try {
            String toolName = getToolName(callback);
            
            // This is the key method - access the tool definition directly
            Object toolDefinition = callback.getToolDefinition();
            
            if (toolDefinition != null) {
                String definitionStr = toolDefinition.toString();
                log.info("Raw tool definition for {}: {}", toolName, 
                    definitionStr.length() > 500 ? definitionStr.substring(0, 500) + "..." : definitionStr);
                
                // Extract description from the tool definition
                String description = extractDescriptionFromToolDefinition(definitionStr);
                if (description != null && description.length() > 10) {
                    log.info("Extracted description for {}: {} characters", toolName, description.length());
                    return description;
                }
                
                // Fallback to full definition string if no description found
                log.debug("No description found, using full definition string for {}", toolName);
                return definitionStr;
            }
            
        } catch (Exception e) {
            log.debug("Error getting tool definition: {}", e.getMessage());
        }
        
        // Final fallback
        String toolName = getToolName(callback);
        return "MCP tool: " + toolName;
    }
    
    /**
     * Extract name from tool definition string
     * Looking for patterns like: DefaultToolDefinition[name=getPickPackage, description=...]
     */
    private String extractNameFromDefinition(String definitionStr) {
        try {
            // Look for "name=toolname" pattern (from DefaultToolDefinition toString())
            int nameIndex = definitionStr.indexOf("name=");
            if (nameIndex >= 0) {
                int valueStart = nameIndex + 5; // length of "name="
                int valueEnd = definitionStr.indexOf(",", valueStart);
                if (valueEnd < 0) valueEnd = definitionStr.indexOf("]", valueStart);
                if (valueEnd < 0) valueEnd = definitionStr.indexOf(" ", valueStart);
                
                if (valueEnd > valueStart) {
                    String name = definitionStr.substring(valueStart, valueEnd).trim();
                    if (!name.isEmpty()) {
                        log.info("🎯 Extracted tool name: {} from definition", name);
                        return name;
                    }
                }
            }
            
            log.warn("⚠️ Could not extract name from definition: {}", definitionStr.length() > 200 ? definitionStr.substring(0, 200) + "..." : definitionStr);
            
            // Fallback: Look for JSON-style "name": "toolname" pattern
            int jsonNameIndex = definitionStr.indexOf("\"name\"");
            if (jsonNameIndex >= 0) {
                int valueStart = definitionStr.indexOf(":", jsonNameIndex) + 1;
                valueStart = definitionStr.indexOf("\"", valueStart) + 1;
                int valueEnd = definitionStr.indexOf("\"", valueStart);
                
                if (valueStart > 0 && valueEnd > valueStart) {
                    String name = definitionStr.substring(valueStart, valueEnd).trim();
                    if (!name.isEmpty()) {
                        log.debug("Extracted tool name (JSON style): {}", name);
                        return name;
                    }
                }
            }
            
        } catch (Exception e) {
            log.debug("Error extracting name from definition: {}", e.getMessage());
        }
        
        return "unknown_tool";
    }
    
    /**
     * Extract description from tool definition string
     */
    private String extractDescriptionFromToolDefinition(String definitionStr) {
        try {
            // Handle DefaultToolDefinition format: DefaultToolDefinition[name=toolName, description=...]
            if (definitionStr.contains("DefaultToolDefinition[") && definitionStr.contains("description=")) {
                int descStart = definitionStr.indexOf("description=");
                if (descStart >= 0) {
                    int valueStart = descStart + 12; // length of "description="
                    int valueEnd = definitionStr.lastIndexOf("]");
                    if (valueEnd < 0) valueEnd = definitionStr.length();
                    
                    if (valueStart < valueEnd) {
                        String desc = definitionStr.substring(valueStart, valueEnd).trim();
                        if (!desc.isEmpty()) {
                            log.info("🎯 Extracted COMPLETE description: {} characters", desc.length());
                            return desc;
                        }
                    }
                }
            }
            
            // Handle JSON format if present
            if (definitionStr.contains("\"description\"")) {
                try {
                    JsonNode node = objectMapper.readTree(definitionStr);
                    if (node.has("description")) {
                        String desc = node.get("description").asText();
                        if (desc != null && !desc.trim().isEmpty()) {
                            log.info("🎯 Extracted JSON description: {} characters", desc.length());
                            return desc.trim();
                        }
                    }
                } catch (Exception e) {
                    log.debug("Not valid JSON, trying text extraction");
                }
                
                // Fallback to text extraction for JSON
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
                            log.info("🎯 Extracted text description: {} characters", desc.length());
                            return desc;
                        }
                    }
                }
            }
            
            log.warn("⚠️ Could not extract description from definition format: {}", 
                definitionStr.length() > 100 ? definitionStr.substring(0, 100) + "..." : definitionStr);
            
        } catch (Exception e) {
            log.debug("Error extracting description: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Find the end of a description value in JSON-like text
     */
    private int findDescriptionEnd(String text, int start) {
        try {
            // Skip whitespace and opening quote
            while (start < text.length() && (Character.isWhitespace(text.charAt(start)) || text.charAt(start) == '"')) {
                start++;
            }
            
            // Find closing quote, handling escaped quotes
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
     * Build intelligent tool execution prompt for MCP calls with dynamic parameter understanding
     */
    private String buildToolExecutionPrompt(String toolName, Map<String, Object> parameters) {
        StringBuilder prompt = new StringBuilder();
        
        // Get tool description to understand what it needs
        var tools = discoverAvailableTools();
        String toolDescription = tools.containsKey(toolName) ? 
            tools.get(toolName).getDescription() : "Execute the function";
            
        prompt.append("Execute the ").append(toolName).append(" function. ");
        prompt.append("Tool description: ").append(toolDescription).append("\n\n");
        
        // Build context based on available parameters
        if (parameters != null && !parameters.isEmpty()) {
            String userQuery = (String) parameters.get("userQuery");
            if (userQuery != null) {
                prompt.append("User request: ").append(userQuery).append("\n");
            }
            
            prompt.append("Available parameters: ");
            parameters.entrySet().stream()
                .filter(entry -> !"userQuery".equals(entry.getKey()))
                .forEach(entry -> {
                    prompt.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
                });
            
            // Remove trailing comma and space
            if (prompt.length() > 2 && prompt.substring(prompt.length() - 2).equals(", ")) {
                prompt.setLength(prompt.length() - 2);
            }
        }
        
        prompt.append("\n\nCall the function with the most appropriate parameters based on the tool description and available information.");
        
        return prompt.toString();
    }
    
    /**
     * Extract dynamic parameters from user message based on available tools
     */
    public Map<String, Object> extractSmartParameters(String userMessage, List<String> selectedToolNames) {
        Map<String, Object> parameters = new HashMap<>();
        String message = userMessage.toLowerCase();
        
        // Extract various types of IDs and parameters
        extractIds(message, parameters);
        extractSkuAndSite(message, parameters);
        extractFilters(message, parameters);
        
        // Add the original user message for context
        parameters.put("userQuery", userMessage);
        
        log.debug("Extracted smart parameters from '{}': {}", userMessage, parameters);
        return parameters;
    }
    
    /**
     * Extract various ID patterns from user message
     */
    private void extractIds(String message, Map<String, Object> parameters) {
        // Order ID patterns
        Pattern orderPattern = Pattern.compile("\\b(ord[er]*[-_\\s]*\\d+|\\d{6,}|[a-z]{2,3}[-_]\\d{3,})\\b", Pattern.CASE_INSENSITIVE);
        Matcher orderMatcher = orderPattern.matcher(message);
        if (orderMatcher.find()) {
            parameters.put("orderId", orderMatcher.group(1));
        }
        
        // Pick List ID patterns  
        Pattern pickListPattern = Pattern.compile("\\b(pick[-_\\s]*list[-_\\s]*\\d+|pl[-_]\\d+)\\b", Pattern.CASE_INSENSITIVE);
        Matcher pickListMatcher = pickListPattern.matcher(message);
        if (pickListMatcher.find()) {
            parameters.put("pickListId", pickListMatcher.group(1));
        }
        
        // Pick Package ID patterns
        Pattern pickPackagePattern = Pattern.compile("\\b(pick[-_\\s]*package[-_\\s]*\\d+|pp[-_]\\d+|package[-_\\s]*\\d+)\\b", Pattern.CASE_INSENSITIVE);
        Matcher pickPackageMatcher = pickPackagePattern.matcher(message);
        if (pickPackageMatcher.find()) {
            parameters.put("pickPackageId", pickPackageMatcher.group(1));
        }
        
        // UUID patterns for stock_trace_id
        Pattern uuidPattern = Pattern.compile("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", Pattern.CASE_INSENSITIVE);
        Matcher uuidMatcher = uuidPattern.matcher(message);
        if (uuidMatcher.find()) {
            parameters.put("stockTraceId", uuidMatcher.group());
        }
        
        // Task ID patterns
        Pattern taskPattern = Pattern.compile("\\b(task[-_\\s]*\\d+|t[-_]\\d+)\\b", Pattern.CASE_INSENSITIVE);
        Matcher taskMatcher = taskPattern.matcher(message);
        if (taskMatcher.find()) {
            parameters.put("taskId", taskMatcher.group(1));
        }
    }
    
    /**
     * Extract SKU and site information
     */
    private void extractSkuAndSite(String message, Map<String, Object> parameters) {
        // SKU patterns (various formats)
        Pattern skuPattern = Pattern.compile("\\b(sku[-_\\s]*[a-z0-9\\-]{3,}|[a-z]{2,}[-_][0-9]{2,}|item[-_\\s]*[a-z0-9\\-]{3,})\\b", Pattern.CASE_INSENSITIVE);
        Matcher skuMatcher = skuPattern.matcher(message);
        if (skuMatcher.find()) {
            parameters.put("sku", skuMatcher.group(1));
        }
        
        // Site patterns
        Pattern sitePattern = Pattern.compile("\\b(site[-_\\s]*[a-z0-9]{2,}|warehouse[-_\\s]*[a-z0-9]{2,}|[a-z]{2,}_warehouse)\\b", Pattern.CASE_INSENSITIVE);
        Matcher siteMatcher = sitePattern.matcher(message);
        if (siteMatcher.find()) {
            parameters.put("site", siteMatcher.group(1));
        }
    }
    
    /**
     * Extract search filters and conditions
     */
    private void extractFilters(String message, Map<String, Object> parameters) {
        // Status filters
        if (message.contains("stuck") || message.contains("blocked") || message.contains("failed")) {
            parameters.put("status", "stuck");
        }
        if (message.contains("completed") || message.contains("done") || message.contains("finished")) {
            parameters.put("status", "completed");
        }
        if (message.contains("pending") || message.contains("waiting") || message.contains("queued")) {
            parameters.put("status", "pending");
        }
        
        // Date filters
        if (message.contains("today") || message.contains("24 hours")) {
            parameters.put("timeFilter", "today");
        }
        if (message.contains("yesterday")) {
            parameters.put("timeFilter", "yesterday");
        }
        if (message.contains("last week") || message.contains("7 days")) {
            parameters.put("timeFilter", "week");
        }
        
        // Quantity filters
        Pattern qtyPattern = Pattern.compile("\\b(quantity|qty)[-_\\s]*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
        Matcher qtyMatcher = qtyPattern.matcher(message);
        if (qtyMatcher.find()) {
            parameters.put("quantity", Integer.parseInt(qtyMatcher.group(2)));
        }
        
        // Priority/urgency
        if (message.contains("urgent") || message.contains("priority") || message.contains("critical")) {
            parameters.put("priority", "high");
        }
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
    
    /**
     * Alias for invalidateToolCache() for API consistency
     */
    public void clearToolCache() {
        invalidateToolCache();
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