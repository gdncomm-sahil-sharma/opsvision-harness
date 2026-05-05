package com.opsvision.harness.service;

import com.opsvision.harness.service.ai.ChatService;
import com.opsvision.harness.service.DynamicMcpService;
import com.opsvision.harness.model.dto.SessionContext;
import com.opsvision.harness.model.dto.response.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private final ObjectMapper objectMapper;
    
    @Autowired
    private ChatClient chatClient;
    
    public AIAssistantService(ChatService chatService, DynamicMcpService dynamicMcpService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.dynamicMcpService = dynamicMcpService;
        this.objectMapper = objectMapper;
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
     * Generate structured chat response with text, timelines, and tables
     */
    public ChatResponseData generateStructuredResponse(String userMessage, String model) {
        logger.info("Processing structured chat request with model: {}", model);
        
        // Dynamically discover available tools
        String availableToolsDescription = dynamicMcpService.getToolDescriptionsForLLM();
        
        String structuredPrompt = String.format("""
            You are a helpful AI assistant with access to warehouse management system tools.
            Generate a comprehensive response in JSON format that includes text summary, timeline data, and tabular data when relevant.
            
            %s
            
            IMPORTANT: Return a valid JSON response with the following structure:
            {
              "textResponse": {
                "summary": "A comprehensive summary of the situation or response to the query",
                "bullets": ["Bullet point 1", "Bullet point 2", "Bullet point 3"]
              },
              "timelines": {
                "title": "Relevant Timeline Title",
                "data": [
                  {
                    "date": 1714521600000,
                    "title": "Phase Name",
                    "description": "Description of this phase",
                    "status": "COMPLETED"
                  }
                ]
              },
              "table": {
                "title": "Relevant Table Title",
                "headers": ["Item Name", "Quantity", "Price", "Status"],
                "data": [
                  ["Product A", 2, 500, "COMPLETED"],
                  ["Product B", 1, 1200, "PENDING"],
                  ["Product C", 3, 800, "FAILED"]
                ]
              }
            }
            
            IMPORTANT GUIDELINES:
            - textResponse is ALWAYS required
            - timelines and table are OPTIONAL - only include them if they add meaningful value to the response
            - If the user query doesn't involve processes, workflows, or time-based data, set "timelines": null
            - If the user query doesn't involve structured data that would benefit from tabular presentation, set "table": null
            - Status values for timeline items must be one of: COMPLETED, PENDING, FAILED, NOT_STARTED, CANCELLED
            - Date values should be Unix timestamps in milliseconds
            - Table data should contain mixed types (strings, numbers) as appropriate for each column
            
            Examples of when to include timelines: order processing status, project phases, workflow steps, historical events
            Examples of when to include tables: order details, inventory lists, comparison data, status breakdowns
            Examples of when to omit: general questions, explanations, simple status checks without detailed data
            
            User Query: %s
            """, availableToolsDescription, userMessage);
        
        try {
            logger.info("🔥 Using MCP-enabled ChatClient for structured request: {}", userMessage);
            
            String jsonResponse = chatClient.prompt()
                .system("You are a warehouse management AI assistant. Always respond with valid JSON in the exact format specified.")
                .user(structuredPrompt)
                .call()
                .content();
                
            logger.debug("Received JSON response: {}", jsonResponse);
            
            return parseStructuredResponse(jsonResponse, userMessage);
            
        } catch (Exception e) {
            logger.error("Error processing structured chat request: {}", e.getMessage());
            return createFallbackResponse(userMessage, e);
        }
    }
    
    /**
     * Parse JSON response into structured ChatResponseData
     */
    private ChatResponseData parseStructuredResponse(String jsonResponse, String userMessage) {
        try {
            // Clean the JSON response in case LLM adds extra text
            String cleanJson = extractJsonFromResponse(jsonResponse);
            
            JsonNode rootNode = objectMapper.readTree(cleanJson);
            
            // Parse text response
            TextResponse textResponse = parseTextResponse(rootNode.get("textResponse"));
            
            // Parse timeline
            Timeline timeline = parseTimeline(rootNode.get("timelines"));
            
            // Parse table
            Table table = parseTable(rootNode.get("table"));
            
            return new ChatResponseData(textResponse, timeline, table);
            
        } catch (Exception e) {
            logger.error("Failed to parse JSON response: {}", e.getMessage());
            logger.debug("Original response: {}", jsonResponse);
            return createFallbackResponse(userMessage, e);
        }
    }
    
    /**
     * Extract JSON from LLM response (removes any surrounding text)
     */
    private String extractJsonFromResponse(String response) {
        // Find the first { and last } to extract JSON
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');
        
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        
        return response; // Return as-is if no clear JSON boundaries found
    }
    
    /**
     * Parse text response section
     */
    private TextResponse parseTextResponse(JsonNode node) {
        if (node == null || node.isNull()) {
            return new TextResponse("Response generated successfully", Arrays.asList("Processing completed"));
        }
        
        String summary = node.has("summary") ? node.get("summary").asText() : "Response generated";
        List<String> bullets = new ArrayList<>();
        
        if (node.has("bullets") && node.get("bullets").isArray()) {
            for (JsonNode bullet : node.get("bullets")) {
                bullets.add(bullet.asText());
            }
        } else {
            bullets.add("Processing completed successfully");
        }
        
        return new TextResponse(summary, bullets);
    }
    
    /**
     * Parse timeline section
     */
    private Timeline parseTimeline(JsonNode node) {
        if (node == null || node.isNull()) {
            return null; // Return null when LLM explicitly sets it as null
        }
        
        String title = node.has("title") ? node.get("title").asText() : "Process Timeline";
        List<TimelineItem> timelineItems = new ArrayList<>();
        
        if (node.has("data") && node.get("data").isArray()) {
            for (JsonNode item : node.get("data")) {
                TimelineItem timelineItem = parseTimelineItem(item);
                if (timelineItem != null) {
                    timelineItems.add(timelineItem);
                }
            }
        }
        
        if (timelineItems.isEmpty()) {
            return null; // Return null if no meaningful timeline data
        }
        
        return new Timeline(title, timelineItems);
    }
    
    /**
     * Parse individual timeline item
     */
    private TimelineItem parseTimelineItem(JsonNode item) {
        try {
            long date = item.has("date") ? item.get("date").asLong() : System.currentTimeMillis();
            String title = item.has("title") ? item.get("title").asText() : "Process Step";
            String description = item.has("description") ? item.get("description").asText() : "Step completed";
            
            TimelineStatus status = TimelineStatus.COMPLETED;
            if (item.has("status")) {
                try {
                    status = TimelineStatus.valueOf(item.get("status").asText().toUpperCase());
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid timeline status: {}, using COMPLETED", item.get("status").asText());
                }
            }
            
            return new TimelineItem(date, title, description, status);
        } catch (Exception e) {
            logger.error("Error parsing timeline item: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse table section
     */
    private Table parseTable(JsonNode node) {
        if (node == null || node.isNull()) {
            return null; // Return null when LLM explicitly sets it as null
        }
        
        String title = node.has("title") ? node.get("title").asText() : "Data Summary";
        List<String> headers = new ArrayList<>();
        List<List<Object>> data = new ArrayList<>();
        
        // Parse headers
        if (node.has("headers") && node.get("headers").isArray()) {
            for (JsonNode header : node.get("headers")) {
                headers.add(header.asText());
            }
        }
        
        // Parse data rows
        if (node.has("data") && node.get("data").isArray()) {
            for (JsonNode row : node.get("data")) {
                if (row.isArray()) {
                    List<Object> rowData = new ArrayList<>();
                    for (JsonNode cell : row) {
                        // Preserve the original data types (numbers, strings, booleans)
                        if (cell.isNumber()) {
                            if (cell.isInt()) {
                                rowData.add(cell.asInt());
                            } else if (cell.isLong()) {
                                rowData.add(cell.asLong());
                            } else {
                                rowData.add(cell.asDouble());
                            }
                        } else if (cell.isBoolean()) {
                            rowData.add(cell.asBoolean());
                        } else {
                            rowData.add(cell.asText());
                        }
                    }
                    data.add(rowData);
                }
            }
        }
        
        if (headers.isEmpty() || data.isEmpty()) {
            return null; // Return null if no meaningful table data
        }
        
        return new Table(title, headers, data);
    }
    
    /**
     * Create fallback response when JSON parsing fails
     */
    private ChatResponseData createFallbackResponse(String userMessage, Exception error) {
        logger.warn("Creating fallback response due to error: {}", error.getMessage());
        
        // Create simple text response
        TextResponse textResponse = new TextResponse(
            "I've processed your request: " + userMessage,
            Arrays.asList(
                "Response generated successfully",
                "Using fallback format due to processing constraints",
                "Please try again if you need more detailed information"
            )
        );
        
        // For fallback responses, don't include timelines/tables since we don't have meaningful structured data
        return new ChatResponseData(textResponse, null, null);
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