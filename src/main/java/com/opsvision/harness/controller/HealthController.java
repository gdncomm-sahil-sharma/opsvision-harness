package com.opsvision.harness.controller;

import com.opsvision.harness.service.DynamicMcpService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private DynamicMcpService dynamicMcpService;
    
    @Autowired
    private ChatModel chatModel;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        boolean overallHealth = true;
        
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "opsvision-harness");
        health.put("version", "1.0.0");
        
        // Database health
        try {
            try (Connection connection = dataSource.getConnection()) {
                boolean dbHealthy = connection.isValid(5);
                health.put("database", dbHealthy ? "UP" : "DOWN");
                if (!dbHealthy) overallHealth = false;
            }
        } catch (Exception e) {
            health.put("database", "DOWN - " + e.getMessage());
            overallHealth = false;
        }
        
        // Redis health
        try {
            redisTemplate.opsForValue().set("health:check", "test");
            String result = (String) redisTemplate.opsForValue().get("health:check");
            boolean redisHealthy = "test".equals(result);
            health.put("redis", redisHealthy ? "UP" : "DOWN");
            if (!redisHealthy) overallHealth = false;
            
            // Clean up
            redisTemplate.delete("health:check");
        } catch (Exception e) {
            health.put("redis", "DOWN - " + e.getMessage());
            overallHealth = false;
        }
        
        // MCP Dynamic Service health
        try {
            var availableTools = dynamicMcpService.discoverAvailableTools();
            boolean mcpHealthy = !availableTools.isEmpty();
            health.put("mcp_tools", mcpHealthy ? 
                "UP - " + availableTools.size() + " tools available" : "DOWN - No tools discovered");
            if (!mcpHealthy) overallHealth = false;
        } catch (Exception e) {
            health.put("mcp_tools", "DOWN - " + e.getMessage());
            overallHealth = false;
        }
        
        // AI Service health — bean wiring only; avoid a live LLM round trip on every probe
        try {
            boolean aiHealthy = chatModel != null;
            health.put("ai_service", aiHealthy ? "UP" : "DOWN");
            if (!aiHealthy) overallHealth = false;
        } catch (Exception e) {
            health.put("ai_service", "DOWN - " + e.getMessage());
            overallHealth = false;
        }
        
        health.put("status", overallHealth ? "UP" : "DOWN");
        
        return ResponseEntity.ok(health);
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> readiness = new HashMap<>();
        
        // Basic readiness check - service is ready if it can handle requests
        readiness.put("status", "READY");
        readiness.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(readiness);
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> live() {
        Map<String, Object> liveness = new HashMap<>();
        
        // Basic liveness check - service is alive
        liveness.put("status", "ALIVE");
        liveness.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(liveness);
    }
}