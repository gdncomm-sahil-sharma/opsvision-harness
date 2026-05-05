package com.opsvision.harness.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Health monitor for MCP session stability
 */
@Component
public class McpSessionHealthMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(McpSessionHealthMonitor.class);
    
    @Autowired
    private DynamicMcpService dynamicMcpService;
    
    private long lastSuccessfulCheck = System.currentTimeMillis();
    private int consecutiveFailures = 0;
    
    /**
     * Periodic health check of MCP session
     */
    @Scheduled(fixedDelay = 60000) // Every 60 seconds
    public void performHealthCheck() {
        try {
            log.debug("Performing MCP session health check...");
            
            // Try to discover tools as a lightweight health check
            var tools = dynamicMcpService.discoverAvailableTools();
            
            if (!tools.isEmpty()) {
                log.debug("MCP session health check passed - {} tools available", tools.size());
                lastSuccessfulCheck = System.currentTimeMillis();
                consecutiveFailures = 0;
            } else {
                handleHealthCheckFailure("No tools discovered");
            }
            
        } catch (Exception e) {
            handleHealthCheckFailure(e.getMessage());
        }
    }
    
    /**
     * Handle health check failures
     */
    private void handleHealthCheckFailure(String error) {
        consecutiveFailures++;
        long timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessfulCheck;
        
        log.warn("MCP session health check failed (failure #{}, {}ms since last success): {}", 
            consecutiveFailures, timeSinceLastSuccess, error);
        
        // If we have multiple failures, proactively refresh the session
        if (consecutiveFailures >= 2) {
            log.info("Multiple consecutive failures detected, invalidating MCP tool cache to force session refresh");
            dynamicMcpService.invalidateToolCache();
        }
        
        // Alert if session has been unhealthy for too long
        if (timeSinceLastSuccess > 300000) { // 5 minutes
            log.error("MCP session has been unhealthy for {}ms - manual intervention may be required", 
                timeSinceLastSuccess);
        }
    }
    
    /**
     * Get session health metrics
     */
    public SessionHealthMetrics getHealthMetrics() {
        return new SessionHealthMetrics(
            lastSuccessfulCheck,
            consecutiveFailures,
            System.currentTimeMillis() - lastSuccessfulCheck
        );
    }
    
    /**
     * Session health metrics DTO
     */
    public static class SessionHealthMetrics {
        private final long lastSuccessfulCheck;
        private final int consecutiveFailures;
        private final long timeSinceLastSuccess;
        
        public SessionHealthMetrics(long lastSuccessfulCheck, int consecutiveFailures, long timeSinceLastSuccess) {
            this.lastSuccessfulCheck = lastSuccessfulCheck;
            this.consecutiveFailures = consecutiveFailures;
            this.timeSinceLastSuccess = timeSinceLastSuccess;
        }
        
        public long getLastSuccessfulCheck() { return lastSuccessfulCheck; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public long getTimeSinceLastSuccess() { return timeSinceLastSuccess; }
        public boolean isHealthy() { return consecutiveFailures == 0 && timeSinceLastSuccess < 120000; }
    }
}