package com.opsvision.harness.service.mcp;

import com.opsvision.harness.exception.McpClientException;
import com.opsvision.harness.model.dto.McpRequest;
import com.opsvision.harness.model.dto.ToolResult;
import com.opsvision.harness.model.enums.ToolExecutionStatus;
import com.opsvision.harness.model.enums.ToolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

@Component
public class ToolInvoker {
    
    private static final Logger log = LoggerFactory.getLogger(ToolInvoker.class);
    
    @Autowired
    private WebClient mcpWebClient;
    
    @Autowired
    private McpResponseParser responseParser;
    
    @Autowired
    private Duration mcpTimeout;

    public ToolResult getOrderTimeline(String orderId) {
        Map<String, Object> parameters = Map.of("order_id", orderId);
        return invokeTool(ToolType.GET_ORDER_TIMELINE, parameters);
    }

    public ToolResult getTaskHistory(String orderId) {
        Map<String, Object> parameters = Map.of("order_id", orderId);
        return invokeTool(ToolType.GET_TASK_HISTORY, parameters);
    }

    public ToolResult getInventoryHistory(String orderId) {
        Map<String, Object> parameters = Map.of("order_id", orderId);
        return invokeTool(ToolType.GET_INVENTORY_HISTORY, parameters);
    }

    public ToolResult getAuditEvents(String orderId) {
        Map<String, Object> parameters = Map.of("order_id", orderId);
        return invokeTool(ToolType.GET_AUDIT_EVENTS, parameters);
    }

    private ToolResult invokeTool(ToolType toolType, Map<String, Object> parameters) {
        long startTime = System.currentTimeMillis();
        
        log.debug("Invoking MCP tool: {} with parameters: {}", toolType.getToolName(), parameters);
        
        McpRequest request = new McpRequest(
            "tools/call", 
            new McpRequest.McpParams(toolType.getToolName(), parameters)
        );

        try {
            String responseBody = mcpWebClient.post()
                .uri("/tools/call")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(mcpTimeout)
                .retryWhen(createRetrySpec())
                .doOnError(error -> log.error("MCP tool {} failed: {}", toolType.getToolName(), error.getMessage()))
                .block();

            long executionTime = System.currentTimeMillis() - startTime;
            
            return responseParser.parseResponse(responseBody, toolType, parameters, executionTime);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return createErrorResult(toolType, parameters, e, executionTime);
        }
    }

    private ToolResult createErrorResult(ToolType toolType, Map<String, Object> parameters, 
                                        Exception error, long executionTime) {
        ToolResult toolResult = new ToolResult(toolType, parameters);
        toolResult.setExecutionTimeMs((int) executionTime);
        
        if (error instanceof WebClientResponseException webClientError) {
            if (webClientError.getStatusCode().is5xxServerError()) {
                toolResult.setStatus(ToolExecutionStatus.FAILED);
                toolResult.setErrorMessage("MCP server error: " + webClientError.getMessage());
            } else if (webClientError.getStatusCode().value() == HttpStatus.REQUEST_TIMEOUT.value()) {
                toolResult.setStatus(ToolExecutionStatus.TIMEOUT);
                toolResult.setErrorMessage("MCP request timeout");
            } else {
                toolResult.setStatus(ToolExecutionStatus.FAILED);
                toolResult.setErrorMessage("MCP client error: " + webClientError.getMessage());
            }
        } else {
            toolResult.setStatus(ToolExecutionStatus.FAILED);
            toolResult.setErrorMessage("Unexpected error: " + error.getMessage());
        }
        
        log.error("MCP tool {} failed after {}ms: {}", 
                 toolType.getToolName(), executionTime, toolResult.getErrorMessage());
        
        return toolResult;
    }

    private Retry createRetrySpec() {
        return Retry.backoff(3, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(8))
            .filter(this::shouldRetry)
            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> 
                new McpClientException("Max retries exceeded: " + retrySignal.failure().getMessage(), 
                                     retrySignal.failure()));
    }

    private boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webClientError) {
            var status = webClientError.getStatusCode();
            // Retry on 5xx server errors and timeouts, but not on 4xx client errors
            return status.is5xxServerError() || status.value() == HttpStatus.REQUEST_TIMEOUT.value();
        }
        
        // Retry on connection issues and timeouts
        String message = throwable.getMessage();
        return message != null && (
            message.toLowerCase().contains("timeout") ||
            message.toLowerCase().contains("connection") ||
            message.toLowerCase().contains("refused")
        );
    }
}