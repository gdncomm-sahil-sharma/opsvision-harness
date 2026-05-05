package com.opsvision.harness.controller;

import com.opsvision.harness.service.AIAssistantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI Controller - Following Medium Article Pattern
 * 
 * REST endpoints for AI chat and investigation capabilities following the
 * architectural approach from the Medium article.
 */
@RestController
@RequestMapping("/api/ai")
public class AIController {
    
    private static final Logger logger = LoggerFactory.getLogger(AIController.class);
    
    private final AIAssistantService aiAssistantService;
    
    @Autowired
    private ChatClient chatClient;

    public AIController(AIAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        logger.info("Processing chat request: {}", request.getMessage());
        
        try {
            String model = request.getModel() != null ? request.getModel() : "openai";
            String response = aiAssistantService.chat(request.getMessage(), model);
            return ResponseEntity.ok(new ChatResponse(response, "success", model));
        } catch (Exception e) {
            logger.error("Error processing chat request: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(new ChatResponse("Error: " + e.getMessage(), "error", "unknown"));
        }
    }

    @PostMapping("/investigate/{orderId}")
    public ResponseEntity<InvestigationResponse> investigate(@PathVariable String orderId) {
        logger.info("Processing investigation request for order: {}", orderId);
        
        try {
            String summary = aiAssistantService.getInvestigationSummary(orderId);
            return ResponseEntity.ok(new InvestigationResponse(orderId, summary, "success"));
        } catch (Exception e) {
            logger.error("Error processing investigation request: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(new InvestigationResponse(orderId, "Error: " + e.getMessage(), "error"));
        }
    }
    
    @PostMapping("/test")
    public ResponseEntity<ChatResponse> testOpenAI(@RequestBody ChatRequest request) {
        logger.info("Testing basic OpenAI functionality: {}", request.getMessage());
        
        try {
            // Use the configured ChatClient directly for basic testing
            String response = chatClient.prompt()
                .user(request.getMessage())
                .call()
                .content();
                
            return ResponseEntity.ok(new ChatResponse(response, "success", "openai"));
        } catch (Exception e) {
            logger.error("OpenAI test failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(new ChatResponse("OpenAI test failed: " + e.getMessage(), "error", "openai"));
        }
    }

    // DTOs following Medium article pattern
    public static class ChatRequest {
        private String message;
        private String model; // "openai" or future model options
        
        // Constructors
        public ChatRequest() {}
        
        public ChatRequest(String message, String model) {
            this.message = message;
            this.model = model;
        }
        
        // Getters and setters
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class ChatResponse {
        private String response;
        private String status;
        private String model;
        
        public ChatResponse(String response, String status, String model) {
            this.response = response;
            this.status = status;
            this.model = model;
        }
        
        // Getters
        public String getResponse() { return response; }
        public String getStatus() { return status; }
        public String getModel() { return model; }
    }

    public static class InvestigationResponse {
        private String orderId;
        private String summary;
        private String status;
        
        public InvestigationResponse(String orderId, String summary, String status) {
            this.orderId = orderId;
            this.summary = summary;
            this.status = status;
        }
        
        // Getters
        public String getOrderId() { return orderId; }
        public String getSummary() { return summary; }
        public String getStatus() { return status; }
    }
}