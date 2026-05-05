package com.opsvision.harness.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.opsvision.harness.model.enums.ToolExecutionStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class ToolResult {
    
    private UUID executionId;
    private String toolName;
    private Map<String, Object> parameters;
    private JsonNode result;
    private ToolExecutionStatus status;
    private String errorMessage;
    private Integer executionTimeMs;
    private LocalDateTime executedAt;

    public ToolResult() {}

    public ToolResult(String toolName, Map<String, Object> parameters) {
        this.toolName = toolName;
        this.parameters = parameters;
        this.status = ToolExecutionStatus.PENDING;
        this.executedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getExecutionId() {
        return executionId;
    }

    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }


    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public JsonNode getResult() {
        return result;
    }

    public void setResult(JsonNode result) {
        this.result = result;
    }

    public ToolExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ToolExecutionStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Integer executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public boolean isSuccess() {
        return status == ToolExecutionStatus.SUCCESS;
    }

    public boolean isError() {
        return status == ToolExecutionStatus.FAILED || status == ToolExecutionStatus.TIMEOUT;
    }
}