package com.opsvision.harness.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class InvestigationResponse {
    
    private UUID sessionId;
    private String response;
    private List<ToolResult> toolResults;
    private long executionTimeMs;
    private boolean success;
    private LocalDateTime timestamp;
    private String errorMessage;

    public InvestigationResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public InvestigationResponse(UUID sessionId, String response, List<ToolResult> toolResults, 
                               long executionTimeMs, boolean success) {
        this.sessionId = sessionId;
        this.response = response;
        this.toolResults = toolResults;
        this.executionTimeMs = executionTimeMs;
        this.success = success;
        this.timestamp = LocalDateTime.now();
    }

    public InvestigationResponse(UUID sessionId, String errorMessage, boolean success) {
        this.sessionId = sessionId;
        this.errorMessage = errorMessage;
        this.success = success;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public List<ToolResult> getToolResults() {
        return toolResults;
    }

    public void setToolResults(List<ToolResult> toolResults) {
        this.toolResults = toolResults;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getToolResultsCount() {
        return toolResults != null ? toolResults.size() : 0;
    }

    public long getSuccessfulToolsCount() {
        return toolResults != null ? 
            toolResults.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum() : 0;
    }

    @Override
    public String toString() {
        return "InvestigationResponse{" +
                "sessionId=" + sessionId +
                ", success=" + success +
                ", executionTimeMs=" + executionTimeMs +
                ", toolResultsCount=" + getToolResultsCount() +
                ", timestamp=" + timestamp +
                '}';
    }
}