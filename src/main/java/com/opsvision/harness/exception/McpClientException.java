package com.opsvision.harness.exception;

/**
 * Exception thrown when MCP client operations fail
 * Used for demo-friendly error handling in conversational interface
 */
public class McpClientException extends RuntimeException {
    
    public McpClientException(String message) {
        super(message);
    }
    
    public McpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}