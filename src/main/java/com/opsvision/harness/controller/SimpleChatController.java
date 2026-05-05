package com.opsvision.harness.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.opsvision.harness.service.SimpleChatService;

/**
 * 🤖 Simple Chat Controller
 * 
 * One endpoint to rule them all. LLM decides everything.
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class SimpleChatController {
    
    private static final Logger log = LoggerFactory.getLogger(SimpleChatController.class);
    
    @Autowired
    private SimpleChatService chatService;
    
    /**
     * 💬 Universal Chat Endpoint
     * 
     * Send any message. LLM figures out what you need and handles everything.
     */
    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatMessage message) {
        log.info("Processing message from user: {} - '{}'", 
                 message.getUserId(), truncate(message.getMessage()));
        
        try {
            var response = chatService.processMessage(message.getUserId(), message.getMessage());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Chat failed for user {}: {}", message.getUserId(), e.getMessage(), e);
            return ResponseEntity.ok().body(new ChatResponse(
                "I'm having trouble right now. Please try again.",
                false,
                e.getMessage()
            ));
        }
    }
    
    /**
     * 📋 Sample Queries
     */
    @GetMapping("/samples")
    public ResponseEntity<?> getSamples() {
        return ResponseEntity.ok().body(java.util.Map.of(
            "samples", java.util.Arrays.asList(
                "What happened with order 12345?",
                "Check inventory for SKU ABC123", 
                "Show me shipping delays today",
                "Investigate order XYZ789",
                "Any issues with warehouse operations?"
            ),
            "instructions", "Just ask me anything about warehouse operations in plain English"
        ));
    }
    
    /**
     * 🔧 Health Check
     */
    @GetMapping("/health") 
    public ResponseEntity<?> health() {
        try {
            boolean healthy = chatService.isHealthy();
            return ResponseEntity.ok().body(java.util.Map.of(
                "status", healthy ? "UP" : "DOWN",
                "service", "Simple Chat API"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok().body(java.util.Map.of(
                "status", "DOWN",
                "error", e.getMessage()
            ));
        }
    }
    
    private String truncate(String msg) {
        return msg != null && msg.length() > 50 ? msg.substring(0, 50) + "..." : msg;
    }
    
    // Simple DTOs
    public static class ChatMessage {
        private String userId;
        private String message;
        
        public ChatMessage() {}
        
        // Getters/Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getMessage() { return message; }  
        public void setMessage(String message) { this.message = message; }
    }
    
    public static class ChatResponse {
        private String response;
        private boolean success;
        private String error;
        private java.util.List<String> toolsUsed;
        
        public ChatResponse() {}
        
        public ChatResponse(String response, boolean success, String error) {
            this.response = response;
            this.success = success;
            this.error = error;
        }
        
        // Getters/Setters
        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public java.util.List<String> getToolsUsed() { return toolsUsed; }
        public void setToolsUsed(java.util.List<String> toolsUsed) { this.toolsUsed = toolsUsed; }
    }
}