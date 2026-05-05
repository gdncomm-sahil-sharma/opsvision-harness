package com.opsvision.harness.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.opsvision.harness.controller.SimpleChatController.ChatResponse;
import com.opsvision.harness.model.dto.ToolResult;
import com.opsvision.harness.model.enums.ToolType;
import com.opsvision.harness.service.mcp.McpClientService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 🤖 Simple Chat Service
 * 
 * Loads LLM responsibilities from prompt file.
 * Lets LLM decide everything and invoke tools.
 */
@Service
public class SimpleChatService {
    
    private static final Logger log = LoggerFactory.getLogger(SimpleChatService.class);
    
    @Autowired
    private ChatClient chatClient;
    
    @Autowired
    private McpClientService mcpClientService;
    
    private String llmPrompt;
    
    public SimpleChatService() {
        loadPrompt();
    }
    
    /**
     * 💬 Process any user message
     * LLM decides what to do and actually executes tools with real analysis
     */
    public ChatResponse processMessage(String userId, String message) {
        log.info("Processing message for user {}: {}", userId, message);
        
        try {
            // Step 1: Extract order ID or relevant parameters from message
            String orderId = extractOrderId(message);
            
            // Step 2: Determine which tools to call based on message intent
            List<ToolType> toolsToCall = determineToolsFromMessage(message);
            
            // Step 3: Actually invoke the tools and get real data
            List<ToolResult> toolResults = new ArrayList<>();
            if (!toolsToCall.isEmpty() && orderId != null) {
                toolResults = mcpClientService.invokeTools(toolsToCall, orderId);
                log.info("Invoked {} tools for order {}, got {} results", 
                         toolsToCall.size(), orderId, toolResults.size());
            }
            
            // Step 4: Let LLM analyze the actual tool results  
            String analysis = generateAnalysisWithResults(message, toolResults);
            
            // Step 5: Build response with real data
            var chatResponse = new ChatResponse();
            chatResponse.setResponse(analysis);
            chatResponse.setSuccess(true);
            chatResponse.setToolsUsed(toolResults.stream()
                .map(tr -> tr.getToolType().name().toLowerCase())
                .collect(Collectors.toList()));
            
            log.info("Successfully processed message for user {} with {} tools", userId, toolResults.size());
            return chatResponse;
            
        } catch (Exception e) {
            log.error("Failed to process message for user {}: {}", userId, e.getMessage(), e);
            return new ChatResponse(
                "I encountered an issue processing your request. Please try again.",
                false,
                e.getMessage()
            );
        }
    }
    
    /**
     * 🔧 Health Check
     */
    public boolean isHealthy() {
        try {
            // Simple health check - try to call LLM
            var response = chatClient.prompt()
                .user("Health check - respond with 'OK'")
                .call()
                .content();
                
            return response != null && response.contains("OK");
            
        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Load LLM prompt from file
     */
    private void loadPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("llm-prompt.txt");
            this.llmPrompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("Loaded LLM prompt: {} characters", llmPrompt.length());
            
        } catch (IOException e) {
            log.error("Failed to load LLM prompt file: {}", e.getMessage());
            // Fallback prompt
            this.llmPrompt = "You are a warehouse assistant. Help with order and inventory questions.";
        }
    }
    
    /**
     * Extract order ID from user message
     */
    private String extractOrderId(String message) {
        // Look for patterns like "order 12345", "order ABC123", etc.
        Pattern orderPattern = Pattern.compile("(?i)order\\s+([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
        var matcher = orderPattern.matcher(message);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Look for standalone alphanumeric IDs (common pattern)
        Pattern idPattern = Pattern.compile("\\b([A-Z]{3}\\d{3}|\\d{4,6})\\b");
        var idMatcher = idPattern.matcher(message);
        
        if (idMatcher.find()) {
            return idMatcher.group(1);
        }
        
        return "12345"; // Default for demo purposes
    }
    
    /**
     * Determine which tools to call based on message content
     */
    private List<ToolType> determineToolsFromMessage(String message) {
        String lowerMessage = message.toLowerCase();
        List<ToolType> tools = new ArrayList<>();
        
        // Order-related queries
        if (lowerMessage.contains("order") || lowerMessage.contains("timeline") || 
            lowerMessage.contains("happened") || lowerMessage.contains("status")) {
            tools.add(ToolType.GET_ORDER_TIMELINE);
            tools.add(ToolType.GET_TASK_HISTORY);
        }
        
        // Inventory queries
        if (lowerMessage.contains("inventory") || lowerMessage.contains("stock") || 
            lowerMessage.contains("sku")) {
            tools.add(ToolType.GET_INVENTORY_HISTORY);
        }
        
        // Investigation/audit queries  
        if (lowerMessage.contains("investigate") || lowerMessage.contains("issue") || 
            lowerMessage.contains("problem") || lowerMessage.contains("audit")) {
            tools.add(ToolType.GET_AUDIT_EVENTS);
            if (!tools.contains(ToolType.GET_ORDER_TIMELINE)) {
                tools.add(ToolType.GET_ORDER_TIMELINE);
            }
        }
        
        // General warehouse queries
        if (lowerMessage.contains("warehouse") || lowerMessage.contains("operations")) {
            tools.add(ToolType.GET_TASK_HISTORY);
            tools.add(ToolType.GET_AUDIT_EVENTS);
        }
        
        // Default to order timeline for any order-specific question
        if (tools.isEmpty() && extractOrderId(message) != null) {
            tools.add(ToolType.GET_ORDER_TIMELINE);
        }
        
        return tools;
    }
    
    /**
     * Generate analysis using LLM with actual tool results
     */
    private String generateAnalysisWithResults(String originalMessage, List<ToolResult> toolResults) {
        if (toolResults.isEmpty()) {
            return "I couldn't retrieve specific data for your request, but I'm here to help with warehouse operations. Could you provide more details?";
        }
        
        StringBuilder prompt = new StringBuilder();
        prompt.append(llmPrompt);
        prompt.append("\n\nUSER QUESTION: ").append(originalMessage);
        prompt.append("\n\nTOOL EXECUTION RESULTS:\n");
        
        for (ToolResult result : toolResults) {
            prompt.append("- ").append(result.getToolType().name()).append(": ");
            if (result.isSuccess()) {
                prompt.append("SUCCESS - ").append(formatToolResult(result));
            } else {
                prompt.append("FAILED - ").append(result.getErrorMessage());
            }
            prompt.append("\n");
        }
        
        prompt.append("\nBased on these ACTUAL results, provide a comprehensive analysis. Be specific about what the data shows:");
        
        try {
            return chatClient.prompt()
                .user(prompt.toString())
                .call()
                .content();
        } catch (Exception e) {
            log.error("Failed to generate analysis: {}", e.getMessage());
            return "I retrieved some data but had trouble analyzing it. The tools executed: " + 
                   toolResults.stream().map(tr -> tr.getToolType().name()).collect(Collectors.joining(", "));
        }
    }
    
    /**
     * Format tool result data for LLM analysis
     */
    private String formatToolResult(ToolResult result) {
        if (result.getResult() != null) {
            // Truncate large results for prompt efficiency
            String resultStr = result.getResult().toString();
            return resultStr.length() > 500 ? resultStr.substring(0, 500) + "..." : resultStr;
        }
        return "No data returned";
    }
}