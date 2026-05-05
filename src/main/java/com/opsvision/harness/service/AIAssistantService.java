package com.opsvision.harness.service;

import com.opsvision.harness.service.ai.ChatService;
import com.opsvision.harness.service.DynamicMcpService;
import com.opsvision.harness.model.dto.SessionContext;
import java.util.stream.Collectors;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AI Assistant Service - Following Medium Article Pattern
 * 
 * This service integrates ChatClient with MCP tool callbacks following the 
 * architectural pattern from the Medium article. Adapted to work with our
 * existing OpenAI integration and MCP client setup.
 */
@Service
public class AIAssistantService {
    
    private static final Logger logger = LoggerFactory.getLogger(AIAssistantService.class);
    
    private final ChatService chatService;
    private final DynamicMcpService dynamicMcpService;
    
    @Autowired
    private ChatClient chatClient;
    
    public AIAssistantService(ChatService chatService, DynamicMcpService dynamicMcpService) {
        this.chatService = chatService;
        this.dynamicMcpService = dynamicMcpService;
    }
    
    /**
     * Enhanced chat method that follows Medium article pattern
     * Integrates AI model with MCP tool capabilities
     */
    public String chat(String userMessage, String model) {
        logger.info("Processing chat request with model: {}", model);
        
        // Dynamically discover available tools
        String availableToolsDescription = dynamicMcpService.getToolDescriptionsForLLM();
        
        String systemPrompt = String.format("""
            You are a helpful AI assistant with access to warehouse management system tools.
            Use the available tools to provide accurate and helpful responses about warehouse operations.
            Always explain what data you're accessing and why.
            
            %s
            
            When a user asks about warehouse operations, use the appropriate tools to gather comprehensive information
            and provide a detailed analysis of the situation.
            """, availableToolsDescription);
        
        try {
            // Use ChatClient with MCP tools directly - as per Medium article pattern
            logger.info("🔥 Using MCP-enabled ChatClient for request: {}", userMessage);
            
            return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
            
        } catch (Exception e) {
            logger.error("Error processing chat request: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * Get investigation summary using MCP tools
     * This demonstrates the enhanced capabilities possible with MCP integration
     */
    public String getInvestigationSummary(String orderId) {
        logger.info("Generating investigation summary for order: {}", orderId);
        
        try {
            // Use dynamic MCP service to gather comprehensive data
            // Get all available tools and execute them for investigation data
            var availableTools = dynamicMcpService.discoverAvailableTools();
            var toolNames = new java.util.ArrayList<>(availableTools.keySet());
            var toolResults = dynamicMcpService.executeSelectedTools(toolNames, orderId);
            String mcpData = toolResults.stream()
                .map(result -> String.format("=== %s ===\n%s", result.getToolName(), result.getResult()))
                .collect(Collectors.joining("\n\n"));
            
            String investigationPrompt = """
                Analyze this warehouse investigation data and provide a comprehensive summary:
                
                Data: %s
                
                Please provide:
                1. Current status overview
                2. Key issues identified
                3. Root cause analysis
                4. Recommended actions
                
                Format the response in a clear, structured manner suitable for operations teams.
                """.formatted(mcpData);
            
            SessionContext context = new SessionContext();
            return chatService.generateInvestigation(context, investigationPrompt);
            
        } catch (Exception e) {
            logger.error("Error generating investigation summary: {}", e.getMessage());
            return "Error generating investigation summary: " + e.getMessage();
        }
    }
}