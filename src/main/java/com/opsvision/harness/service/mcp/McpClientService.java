package com.opsvision.harness.service.mcp;

import com.opsvision.harness.exception.McpClientException;
import com.opsvision.harness.model.dto.ToolResult;
import com.opsvision.harness.model.enums.ToolExecutionStatus;
import com.opsvision.harness.model.enums.ToolType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class McpClientService {
    
    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);
    
    @Autowired
    private ToolInvoker toolInvoker;
    
    @Autowired
    private InternalToolService internalToolService;

    @CircuitBreaker(name = "mcp-client", fallbackMethod = "fallbackInvokeTools")
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
        
        // First try external MCP server
        ToolResult result = switch (toolType) {
            case GET_ORDER_TIMELINE -> toolInvoker.getOrderTimeline(orderId);
            case GET_TASK_HISTORY -> toolInvoker.getTaskHistory(orderId);
            case GET_INVENTORY_HISTORY -> toolInvoker.getInventoryHistory(orderId);
            case GET_AUDIT_EVENTS -> toolInvoker.getAuditEvents(orderId);
        };
        
        // If external MCP call failed, fallback to internal tools
        if (result.getStatus() == ToolExecutionStatus.FAILED) {
            log.info("External MCP tool {} failed, falling back to internal implementation", toolType.getToolName());
            
            result = switch (toolType) {
                case GET_ORDER_TIMELINE -> internalToolService.getOrderTimeline(orderId);
                case GET_TASK_HISTORY -> internalToolService.getTaskHistory(orderId);
                case GET_INVENTORY_HISTORY -> internalToolService.getInventoryHistory(orderId);
                case GET_AUDIT_EVENTS -> internalToolService.getAuditEvents(orderId);
            };
            
            if (result.isSuccess()) {
                log.info("Internal tool {} succeeded for order: {}", toolType.getToolName(), orderId);
            }
        }
        
        return result;
    }

    public List<ToolResult> invokeToolsWithFallback(List<ToolType> toolTypes, String orderId) {
        try {
            return invokeTools(toolTypes, orderId);
        } catch (Exception e) {
            log.warn("MCP tools invocation failed, returning empty results: {}", e.getMessage());
            return createFallbackResults(toolTypes, e.getMessage());
        }
    }

    // Fallback method for circuit breaker
    public List<ToolResult> fallbackInvokeTools(List<ToolType> toolTypes, String orderId, Exception ex) {
        log.error("Circuit breaker activated for MCP client, using internal tools fallback: {}", ex.getMessage());
        
        List<ToolResult> results = new ArrayList<>();
        for (ToolType toolType : toolTypes) {
            try {
                ToolResult result = switch (toolType) {
                    case GET_ORDER_TIMELINE -> internalToolService.getOrderTimeline(orderId);
                    case GET_TASK_HISTORY -> internalToolService.getTaskHistory(orderId);
                    case GET_INVENTORY_HISTORY -> internalToolService.getInventoryHistory(orderId);
                    case GET_AUDIT_EVENTS -> internalToolService.getAuditEvents(orderId);
                };
                
                results.add(result);
                log.debug("Internal fallback tool {} completed successfully", toolType.getToolName());
                
            } catch (Exception internalEx) {
                log.error("Internal tool {} also failed: {}", toolType.getToolName(), internalEx.getMessage());
                
                ToolResult failedResult = new ToolResult(toolType, Map.of("order_id", orderId));
                failedResult.setStatus(ToolExecutionStatus.FAILED);
                failedResult.setErrorMessage("Both external MCP and internal tools failed: " + internalEx.getMessage());
                results.add(failedResult);
            }
        }
        
        return results;
    }

    private List<ToolResult> createFallbackResults(List<ToolType> toolTypes, String errorMessage) {
        return toolTypes.stream()
            .map(toolType -> {
                ToolResult result = new ToolResult(toolType, Map.of());
                result.setStatus(ToolExecutionStatus.FAILED);
                result.setErrorMessage(errorMessage);
                return result;
            })
            .collect(Collectors.toList());
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
            // This will now fallback to internal tools if MCP server is unavailable
            ToolResult result = invokeTool(ToolType.GET_ORDER_TIMELINE, "health-check");
            return result.isSuccess();
        } catch (Exception e) {
            log.warn("MCP and internal tools health check failed: {}", e.getMessage());
            return false;
        }
    }
}