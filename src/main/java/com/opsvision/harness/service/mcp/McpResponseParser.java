package com.opsvision.harness.service.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.harness.exception.McpClientException;
import com.opsvision.harness.model.dto.McpResponse;
import com.opsvision.harness.model.dto.ToolResult;
import com.opsvision.harness.model.enums.ToolExecutionStatus;
import com.opsvision.harness.model.enums.ToolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class McpResponseParser {
    
    private static final Logger log = LoggerFactory.getLogger(McpResponseParser.class);
    
    @Autowired
    private ObjectMapper objectMapper;

    public ToolResult parseResponse(String rawResponse, ToolType toolType, Map<String, Object> parameters, 
                                    long executionTimeMs) {
        ToolResult toolResult = new ToolResult(toolType, parameters);
        toolResult.setExecutionTimeMs((int) executionTimeMs);
        
        try {
            McpResponse mcpResponse = objectMapper.readValue(rawResponse, McpResponse.class);
            
            if (mcpResponse.isSuccess()) {
                return parseSuccessfulResponse(mcpResponse, toolResult);
            } else {
                return parseErrorResponse(mcpResponse, toolResult);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse MCP response for tool {}: {}", toolType.getToolName(), e.getMessage());
            toolResult.setStatus(ToolExecutionStatus.FAILED);
            toolResult.setErrorMessage("Failed to parse MCP response: " + e.getMessage());
            return toolResult;
        }
    }

    private ToolResult parseSuccessfulResponse(McpResponse mcpResponse, ToolResult toolResult) {
        McpResponse.McpResult result = mcpResponse.getResult();
        
        if (result.isError()) {
            toolResult.setStatus(ToolExecutionStatus.FAILED);
            toolResult.setErrorMessage("MCP tool returned error result");
            toolResult.setResult(result.getContent());
            log.warn("MCP tool {} returned error in result", toolResult.getToolName());
        } else {
            toolResult.setStatus(ToolExecutionStatus.SUCCESS);
            toolResult.setResult(result.getContent());
            log.debug("MCP tool {} executed successfully", toolResult.getToolName());
        }
        
        return toolResult;
    }

    private ToolResult parseErrorResponse(McpResponse mcpResponse, ToolResult toolResult) {
        McpResponse.McpError error = mcpResponse.getError();
        
        toolResult.setStatus(ToolExecutionStatus.FAILED);
        
        if (error != null) {
            String errorMsg = String.format("MCP Error [%d]: %s", error.getCode(), error.getMessage());
            toolResult.setErrorMessage(errorMsg);
            toolResult.setResult(error.getData());
            
            log.error("MCP tool {} failed with error: {}", toolResult.getToolName(), errorMsg);
        } else {
            toolResult.setErrorMessage("Unknown MCP error");
            log.error("MCP tool {} failed with unknown error", toolResult.getToolName());
        }
        
        return toolResult;
    }

    public void validateToolResult(ToolResult toolResult) throws McpClientException {
        if (toolResult.getStatus() == ToolExecutionStatus.FAILED) {
            throw new McpClientException(
                toolResult.getToolName(), 
                toolResult.getErrorMessage(), 
                -1
            );
        }
        
        if (toolResult.getResult() == null) {
            throw new McpClientException(
                toolResult.getToolName(), 
                "Tool result is null", 
                -1
            );
        }
    }

    public boolean isRetryableError(ToolResult toolResult) {
        if (toolResult.getStatus() != ToolExecutionStatus.FAILED) {
            return false;
        }
        
        String errorMessage = toolResult.getErrorMessage();
        if (errorMessage == null) {
            return false;
        }
        
        // Retry on timeout, connection errors, or 5xx server errors
        return errorMessage.toLowerCase().contains("timeout") ||
               errorMessage.toLowerCase().contains("connection") ||
               errorMessage.toLowerCase().contains("5") ||
               errorMessage.toLowerCase().contains("server error");
    }
}