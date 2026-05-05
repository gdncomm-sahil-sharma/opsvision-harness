package com.opsvision.harness.controller;

import com.opsvision.harness.model.dto.ChatMessage;
import com.opsvision.harness.model.dto.response.*;
import com.opsvision.harness.service.AIAssistantService;
import com.opsvision.harness.service.DynamicMcpService;
import com.opsvision.harness.service.McpSessionHealthMonitor;
import com.opsvision.harness.service.ToolCallbackTestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class SimpleChatController {

    private static final Logger log = LoggerFactory.getLogger(SimpleChatController.class);

    @Autowired
    private AIAssistantService aiAssistantService;
    
    @Autowired
    private DynamicMcpService dynamicMcpService;
    
    @Autowired
    private McpSessionHealthMonitor sessionHealthMonitor;
    
    @Autowired
    private ToolCallbackTestService testService;

    /**
     * Main chat endpoint - Returns structured response with text, timelines, and tables
     */
    @PostMapping
    public ResponseEntity<StructuredChatResponse> chat(@RequestBody ChatMessage message) {
        try {
            log.info("📨 Received structured chat message from user: {}", message.getUserId());
            log.info("📝 Message content: {}", message.getMessage());
            
            // Use AI Assistant Service to generate structured response
            String model = "openai"; // Default model
            ChatResponseData responseData = aiAssistantService.generateStructuredResponse(message.getMessage(), model);
            
            StructuredChatResponse structuredResponse = new StructuredChatResponse(
                200, 
                true, 
                responseData, 
                System.currentTimeMillis()
            );
            
            log.info("✅ Structured chat response generated successfully");
            return ResponseEntity.ok(structuredResponse);
            
        } catch (Exception e) {
            log.error("❌ Error processing structured chat message", e);
            
            // Create error response with fallback data
            ChatResponseData errorData = createErrorResponseData(e.getMessage());
            StructuredChatResponse errorResponse = new StructuredChatResponse(
                500, 
                false, 
                errorData, 
                System.currentTimeMillis()
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Create error response data for failed requests
     */
    private ChatResponseData createErrorResponseData(String errorMessage) {
        TextResponse textResponse = new TextResponse(
            "An error occurred while processing your request: " + errorMessage,
            java.util.Arrays.asList(
                "Request processing failed",
                "Please try again or contact support if the issue persists",
                "Error details have been logged for investigation"
            )
        );
        
        // For error responses, don't include timelines/tables since they're not meaningful for errors
        return new ChatResponseData(textResponse, null, null);
    }

    /**
     * Get sample chat queries
     */
    @GetMapping("/samples")
    public ResponseEntity<Map<String, Object>> getSamples() {
        Map<String, Object> samples = new HashMap<>();
        samples.put("general_queries", new String[]{
            "What happened with order ORD-12345?",
            "Show me the timeline for order ORD-67890",
            "Are there any issues with order processing today?",
            "What's the status of recent warehouse operations?"
        });
        samples.put("investigation_queries", new String[]{
            "I need to investigate order ORD-99999 for compliance",
            "Audit trail for order ORD-11111 please",
            "Security review needed for order ORD-22222"
        });
        samples.put("inventory_queries", new String[]{
            "Check inventory movements for order ORD-33333",
            "Any stock issues with recent orders?",
            "Inventory discrepancies in the last 24 hours?"
        });
        samples.put("note", "LLM will dynamically discover and select appropriate tools for each query");
        
        return ResponseEntity.ok(samples);
    }

    /**
     * Simple health check for chat API
     */
    @GetMapping("/health") 
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Simple Chat API");
        return ResponseEntity.ok(health);
    }

    /**
     * Get available tools dynamically discovered from MCP server
     */
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> getAvailableTools() {
        try {
            var tools = dynamicMcpService.discoverAvailableTools();
            
            Map<String, Object> response = new HashMap<>();
            response.put("tool_count", tools.size());
            response.put("cache_status", "Tools cached for 5 minutes");
            
            // Create a map of tool names to descriptions
            Map<String, String> toolMap = new HashMap<>();
            for (var entry : tools.entrySet()) {
                toolMap.put(entry.getKey(), entry.getValue().getDescription());
            }
            
            response.put("available_tools", toolMap);
            response.put("discovery_method", "MCP Protocol tools/list");
            response.put("message", "Tools discovered dynamically from MCP server using proper MCP protocol");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get available tools", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to discover tools: " + e.getMessage());
            errorResponse.put("tool_count", 0);
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get MCP session health status
     */
    @GetMapping("/session/health")
    public ResponseEntity<Map<String, Object>> getSessionHealth() {
        try {
            var metrics = sessionHealthMonitor.getHealthMetrics();
            
            Map<String, Object> health = new HashMap<>();
            health.put("healthy", metrics.isHealthy());
            health.put("last_successful_check", new java.util.Date(metrics.getLastSuccessfulCheck()));
            health.put("consecutive_failures", metrics.getConsecutiveFailures());
            health.put("time_since_last_success_ms", metrics.getTimeSinceLastSuccess());
            
            if (metrics.isHealthy()) {
                health.put("status", "UP");
                health.put("message", "MCP session is healthy");
                return ResponseEntity.ok(health);
            } else {
                health.put("status", "DEGRADED");
                health.put("message", "MCP session experiencing issues");
                return ResponseEntity.status(503).body(health);
            }
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "DOWN");
            errorResponse.put("error", "Failed to check session health: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Refresh tool cache
     */
    @PostMapping("/tools/refresh")
    public ResponseEntity<Map<String, Object>> refreshTools() {
        try {
            dynamicMcpService.clearToolCache();
            var tools = dynamicMcpService.discoverAvailableTools();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Tool cache refreshed successfully");
            response.put("tool_count", tools.size());
            response.put("tools", tools.keySet());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to refresh tools", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to refresh tools: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Test endpoint to explore ToolCallback methods
     */
    @GetMapping("/test-toolcallback")
    public ResponseEntity<?> testToolCallback() {
        try {
            testService.exploreToolCallbackMethods();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Tool callback methods explored - check logs");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to explore tool callback methods", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to explore methods: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}