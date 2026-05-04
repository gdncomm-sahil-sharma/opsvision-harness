package com.opsvision.harness.exception;

public class McpClientException extends RuntimeException {
    
    private final String toolName;
    private final int errorCode;

    public McpClientException(String message) {
        super(message);
        this.toolName = null;
        this.errorCode = -1;
    }

    public McpClientException(String message, Throwable cause) {
        super(message, cause);
        this.toolName = null;
        this.errorCode = -1;
    }

    public McpClientException(String toolName, String message, int errorCode) {
        super(String.format("MCP tool '%s' failed: %s", toolName, message));
        this.toolName = toolName;
        this.errorCode = errorCode;
    }

    public McpClientException(String toolName, String message, Throwable cause) {
        super(String.format("MCP tool '%s' failed: %s", toolName, message), cause);
        this.toolName = toolName;
        this.errorCode = -1;
    }

    public String getToolName() {
        return toolName;
    }

    public int getErrorCode() {
        return errorCode;
    }
}