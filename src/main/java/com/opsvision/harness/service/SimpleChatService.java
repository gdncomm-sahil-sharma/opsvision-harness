package com.opsvision.harness.service;

import com.opsvision.harness.model.dto.ChatResponse;
import com.opsvision.harness.service.DynamicMcpService.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SimpleChatService {
    
    private static final Logger log = LoggerFactory.getLogger(SimpleChatService.class);
    
    @Autowired
    private ChatClient chatClient;
    
    @Autowired
    private DynamicMcpService dynamicMcpService;
    
    private String llmPrompt;
    
    /**
     * Load LLM prompt from file
     */
    private String getLLMPrompt() {
        if (llmPrompt == null) {
            try {
                ClassPathResource resource = new ClassPathResource("llm-prompt.txt");
                llmPrompt = resource.getContentAsString(StandardCharsets.UTF_8);
                log.info("✅ LLM prompt loaded successfully");
            } catch (IOException e) {
                log.error("❌ Failed to load LLM prompt file", e);
                llmPrompt = "You are a helpful warehouse assistant.";
            }
        }
        return llmPrompt;
    }

    /**
     * Process user message with dynamic tool discovery and execution
     */
    public ChatResponse processMessage(String userId, String message) {
        try {
            log.info("🔍 Processing message for user: {}", userId);
            
            // Extract order ID if present
            String orderId = extractOrderId(message);
            log.info("📝 Extracted order ID: {}", orderId != null ? orderId : "None");
            
            // Dynamically discover available tools
            String availableToolsDescription = dynamicMcpService.getToolDescriptionsForLLM();
            log.info("🛠️ Available tools discovered dynamically");
            
            // Let LLM choose which tools to invoke
            List<String> selectedTools = letLLMChooseTools(message, availableToolsDescription);
            log.info("🎯 LLM selected tools: {}", selectedTools);
            
            // Execute selected tools and get real data
            List<ToolExecutionResult> toolResults = dynamicMcpService.executeSelectedTools(selectedTools, orderId);
            log.info("⚡ Executed {} tools successfully", toolResults.size());
            
            // Generate intelligent response with actual data analysis
            String analysis = generateAnalysisWithDynamicResults(message, toolResults, availableToolsDescription);
            
            // Build response
            ChatResponse response = new ChatResponse();
            response.setResponse(analysis);
            response.setSuccess(true);
            response.setToolsUsed(selectedTools);
            
            return response;
            
        } catch (Exception e) {
            log.error("❌ Error processing message", e);
            
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError(e.getMessage());
            errorResponse.setResponse("I encountered an error processing your request. Please try again.");
            
            return errorResponse;
        }
    }
    
    /**
     * Let LLM choose tools dynamically based on available tools and user message
     */
    private List<String> letLLMChooseTools(String userMessage, String availableToolsDescription) {
        String toolSelectionPrompt = String.format("""
            Available Tools:
            %s
            
            User Message: "%s"
            
            Based on the user's request and available tools above, respond with ONLY a comma-separated list of tool names to execute.
            
            Choose tools that best match the user's question from the available tools list.
            Multiple tools can be selected if needed.
            
            Respond with tool names only, no explanations:""", 
            availableToolsDescription, userMessage);
            
        try {
            String response = chatClient.prompt()
                .user(toolSelectionPrompt)
                .call()
                .content();
                
            // Parse comma-separated tool names
            List<String> tools = Arrays.stream(response.split(","))
                .map(String::trim)
                .filter(tool -> !tool.isEmpty())
                .collect(Collectors.toList());
                
            log.info("🎯 LLM selected tools: {}", tools);
            return tools;
            
        } catch (Exception e) {
            log.error("❌ Error in tool selection", e);
            return List.of(); // Return empty list if tool selection fails
        }
    }
    
    /**
     * Generate analysis with actual tool execution results
     */
    private String generateAnalysisWithDynamicResults(String userMessage, List<ToolExecutionResult> toolResults, String availableTools) {
        
        // Compile all tool results into analysis context
        String toolResultsContext = toolResults.stream()
            .map(result -> String.format("Tool: %s\nResult: %s", result.getToolName(), result.getResult()))
            .collect(Collectors.joining("\n\n"));
            
        String analysisPrompt = String.format("""
            %s
            
            User Question: "%s"
            
            Available Tools: %s
            
            Tool Execution Results:
            %s
            
            Based on the ACTUAL DATA from tool results above, provide a specific, factual analysis.
            Focus on concrete findings, specific details, and actionable insights.
            Mention specific timestamps, locations, quantities, or error codes when available.
            Keep response under 200 words.""",
            getLLMPrompt(), userMessage, availableTools, toolResultsContext);
            
        try {
            String analysis = chatClient.prompt()
                .user(analysisPrompt)
                .call()
                .content();
                
            log.info("✅ Analysis generated successfully");
            return analysis;
            
        } catch (Exception e) {
            log.error("❌ Error generating analysis", e);
            return "Analysis completed. Please refer to the tool execution results for detailed information.";
        }
    }
    
    /**
     * Extract order ID from user message using regex patterns
     */
    private String extractOrderId(String message) {
        if (message == null) return null;
        
        // Common patterns for order IDs
        Pattern[] patterns = {
            Pattern.compile("\\b(ORD-\\d+)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\border[\\s:]*([A-Z0-9\\-]+)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([A-Z]{2,3}-\\d{3,})\\b"),
            Pattern.compile("\\b(\\d{6,})\\b") // Fallback for numeric IDs
        };
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String orderId = matcher.group(1);
                log.debug("📝 Found order ID: {}", orderId);
                return orderId;
            }
        }
        
        // Default order ID for testing if none found
        return "ORD-12345";
    }
}