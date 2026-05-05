package com.opsvision.harness.controller;

import com.opsvision.harness.model.dto.ChatMessage;
import com.opsvision.harness.model.dto.ChatResponse;
import com.opsvision.harness.service.SimpleChatService;
import com.opsvision.harness.service.DynamicMcpService;
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
    private SimpleChatService chatService;
    
    @Autowired
    private DynamicMcpService dynamicMcpService;

    /**
     * Main chat endpoint - LLM decides everything dynamically
     */
    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatMessage message) {
        try {
            log.info("📨 Received chat message from user: {}", message.getUserId());
            
            var response = chatService.processMessage(message.getUserId(), message.getMessage());
            
            log.info("✅ Chat response generated successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error processing chat message", e);
            var errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError("Internal server error: " + e.getMessage());
            errorResponse.setResponse("Sorry, I encountered an error processing your request.");
            return ResponseEntity.status(500).body(errorResponse);
        }
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
            String toolDescriptions = dynamicMcpService.getToolDescriptionsForLLM();
            
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
}