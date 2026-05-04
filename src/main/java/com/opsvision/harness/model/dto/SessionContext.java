package com.opsvision.harness.model.dto;

import com.opsvision.harness.model.enums.SessionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SessionContext {
    
    private UUID sessionId;
    private String userId;
    private String initialQuery;
    private SessionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Map<String, String> metadata;
    private List<ConversationSummary> conversationHistory;
    private List<ToolResult> toolResults;
    private String aggregatedContext;

    public SessionContext() {}

    public SessionContext(UUID sessionId, String userId, String initialQuery) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.initialQuery = initialQuery;
        this.status = SessionStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getInitialQuery() {
        return initialQuery;
    }

    public void setInitialQuery(String initialQuery) {
        this.initialQuery = initialQuery;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public List<ConversationSummary> getConversationHistory() {
        return conversationHistory;
    }

    public void setConversationHistory(List<ConversationSummary> conversationHistory) {
        this.conversationHistory = conversationHistory;
    }

    public List<ToolResult> getToolResults() {
        return toolResults;
    }

    public void setToolResults(List<ToolResult> toolResults) {
        this.toolResults = toolResults;
    }

    public String getAggregatedContext() {
        return aggregatedContext;
    }

    public void setAggregatedContext(String aggregatedContext) {
        this.aggregatedContext = aggregatedContext;
    }

    public static class ConversationSummary {
        private UUID conversationId;
        private Integer sequenceNumber;
        private String query;
        private String response;
        private LocalDateTime createdAt;

        public ConversationSummary() {}

        public ConversationSummary(UUID conversationId, Integer sequenceNumber, String query, 
                                   String response, LocalDateTime createdAt) {
            this.conversationId = conversationId;
            this.sequenceNumber = sequenceNumber;
            this.query = query;
            this.response = response;
            this.createdAt = createdAt;
        }

        // Getters and Setters
        public UUID getConversationId() {
            return conversationId;
        }

        public void setConversationId(UUID conversationId) {
            this.conversationId = conversationId;
        }

        public Integer getSequenceNumber() {
            return sequenceNumber;
        }

        public void setSequenceNumber(Integer sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}