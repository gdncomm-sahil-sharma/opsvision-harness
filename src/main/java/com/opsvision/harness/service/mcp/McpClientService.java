package com.opsvision.harness.service.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.harness.exception.McpClientException;
import com.opsvision.harness.model.dto.ToolResult;
import com.opsvision.harness.model.enums.ToolExecutionStatus;
import com.opsvision.harness.model.enums.ToolType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class McpClientService {
    
    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);
    
    @Autowired
    private ChatClient chatClient;
    
    @Autowired
    private ChatModel chatModel;
    
    @Autowired(required = false)
    private SyncMcpToolCallbackProvider syncMcpToolCallbackProvider;

    @Autowired(required = false)
    private AsyncMcpToolCallbackProvider asyncMcpToolCallbackProvider;

    @Value("${spring.ai.mcp.enabled:false}")
    private boolean mcpEnabled;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @CircuitBreaker(name = "mcp-client")
    public List<ToolResult> invokeTools(List<ToolType> toolTypes, String orderId) {
        log.info("Invoking {} MCP tools for order: {}", toolTypes.size(), orderId);
        
        // Execute tools in parallel for better performance
        List<CompletableFuture<ToolResult>> futures = toolTypes.stream()
            .map(toolType -> CompletableFuture.supplyAsync(() -> invokeTool(toolType, orderId)))
            .toList();

        // Wait for all tools to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        try {
            // Wait for completion with timeout
            allFutures.get(60, TimeUnit.SECONDS);
            
            // Collect results
            List<ToolResult> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
                
            logExecutionSummary(results);
            return results;
            
        } catch (Exception e) {
            log.error("Failed to complete all tool executions: {}", e.getMessage());
            
            // Collect partial results from completed futures
            List<ToolResult> partialResults = new ArrayList<>();
            for (CompletableFuture<ToolResult> future : futures) {
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    partialResults.add(future.join());
                } else {
                    // Create error result for incomplete futures
                    ToolResult errorResult = new ToolResult();
                    errorResult.setStatus(ToolExecutionStatus.FAILED);
                    errorResult.setErrorMessage("Tool execution timeout or failure");
                    partialResults.add(errorResult);
                }
            }
            
            return partialResults;
        }
    }

    public ToolResult invokeTool(ToolType toolType, String orderId) {
        log.debug("Invoking single tool: {} for order: {}", toolType.getToolName(), orderId);
        
        // Direct Spring AI MCP tool invocation
        return invokeSpringAiTool(toolType, orderId);
    }

    /**
     * Invoke MCP tools using Spring AI ChatClient with registered functions
     */
    private ToolResult invokeSpringAiTool(ToolType toolType, String orderId) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Create a prompt that will trigger the specific function call
            String functionPrompt = switch (toolType) {
                case GET_ORDER_TIMELINE -> "Call the getOrderTimeline function for order " + orderId;
                case GET_TASK_HISTORY -> "Call the getTaskHistory function for order " + orderId;
                case GET_INVENTORY_HISTORY -> "Call the getInventoryHistory function for order " + orderId;
                case GET_AUDIT_EVENTS -> "Call the getAuditEvents function for order " + orderId;
            };
            
            // Create ChatClient with MCP tools if available
            ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);
            
            // Add MCP tool callbacks if MCP is enabled and tool provider is available
            if (mcpEnabled) {
                if (syncMcpToolCallbackProvider != null) {
                    log.info("Using Sync MCP tool callbacks for tool: {}", toolType.getToolName());
                    chatClientBuilder.defaultToolCallbacks(syncMcpToolCallbackProvider);
                } else if (asyncMcpToolCallbackProvider != null) {
                    log.info("Using Async MCP tool callbacks for tool: {}", toolType.getToolName());
                    chatClientBuilder.defaultToolCallbacks(asyncMcpToolCallbackProvider);
                } else {
                    log.warn("MCP is enabled but no tool callback provider is available");
                }
            }
            
            String response = chatClientBuilder.build()
                .prompt()
                .user(functionPrompt)
                .call()
                .content();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            ToolResult result = new ToolResult(toolType, Map.of("orderId", orderId));
            result.setExecutionTimeMs((int) executionTime);
            result.setStatus(ToolExecutionStatus.SUCCESS);
            
            // Try to parse response as JSON
            try {
                JsonNode jsonResult = objectMapper.readTree(response);
                result.setResult(jsonResult);
                log.debug("Successfully invoked function {} in {}ms", toolType.getToolName(), executionTime);
            } catch (Exception e) {
                // If not JSON, store as string  
                result.setResult(objectMapper.createObjectNode().put("response", response));
                log.debug("Function {} returned non-JSON response in {}ms", toolType.getToolName(), executionTime);
            }
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Function {} failed after {}ms: {}", toolType.getToolName(), executionTime, e.getMessage());
            
            ToolResult result = new ToolResult(toolType, Map.of("orderId", orderId));
            result.setExecutionTimeMs((int) executionTime);
            result.setStatus(ToolExecutionStatus.FAILED);
            result.setErrorMessage("Function call error: " + e.getMessage());
            
            return result;
        }
    }




    private void logExecutionSummary(List<ToolResult> results) {
        long successCount = results.stream()
            .mapToLong(result -> result.isSuccess() ? 1 : 0)
            .sum();
            
        long failureCount = results.size() - successCount;
        
        OptionalDouble avgExecutionTime = results.stream()
            .filter(result -> result.getExecutionTimeMs() != null)
            .mapToInt(ToolResult::getExecutionTimeMs)
            .average();
            
        log.info("MCP tools execution summary: {} successful, {} failed, avg execution time: {}ms",
                successCount, failureCount, 
                avgExecutionTime.isPresent() ? String.format("%.0f", avgExecutionTime.getAsDouble()) : "N/A");
    }

    public boolean isHealthy() {
        try {
            // Perform a simple health check by calling a lightweight tool
            ToolResult result = invokeTool(ToolType.GET_ORDER_TIMELINE, "health-check");
            return result.isSuccess();
        } catch (Exception e) {
            log.warn("MCP health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gather comprehensive investigation data using MCP tools
     * Following Medium article pattern for enhanced AI integration
     */
    public String gatherInvestigationData(String orderId) {
        log.info("Gathering investigation data for order: {}", orderId);
        
        try {
            // Use existing invokeTools method for comprehensive data gathering
            List<ToolResult> results = invokeTools(Arrays.asList(
                ToolType.GET_ORDER_TIMELINE,
                ToolType.GET_TASK_HISTORY,
                ToolType.GET_INVENTORY_HISTORY,
                ToolType.GET_AUDIT_EVENTS
            ), orderId);
            
            // Format results as a structured investigation report
            return results.stream()
                .filter(ToolResult::isSuccess)
                .map(result -> "=== " + result.getToolType() + " ===\n" + result.getResult().toString())
                .collect(Collectors.joining("\n\n"));
                
        } catch (Exception e) {
            log.error("Error gathering investigation data: {}", e.getMessage());
            return "Error gathering investigation data: " + e.getMessage();
        }
    }
}