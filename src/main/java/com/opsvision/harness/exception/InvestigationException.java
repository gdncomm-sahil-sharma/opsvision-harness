package com.opsvision.harness.exception;

public class InvestigationException extends RuntimeException {
    
    private final String sessionId;
    private final String userId;

    public InvestigationException(String message) {
        super(message);
        this.sessionId = null;
        this.userId = null;
    }

    public InvestigationException(String message, Throwable cause) {
        super(message, cause);
        this.sessionId = null;
        this.userId = null;
    }

    public InvestigationException(String sessionId, String userId, String message) {
        super(String.format("Investigation failed for session %s (user %s): %s", sessionId, userId, message));
        this.sessionId = sessionId;
        this.userId = userId;
    }

    public InvestigationException(String sessionId, String userId, String message, Throwable cause) {
        super(String.format("Investigation failed for session %s (user %s): %s", sessionId, userId, message), cause);
        this.sessionId = sessionId;
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }
}