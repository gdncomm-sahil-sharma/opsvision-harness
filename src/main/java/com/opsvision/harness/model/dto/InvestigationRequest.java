package com.opsvision.harness.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public class InvestigationRequest {
    
    @NotBlank(message = "Query is required")
    @Size(min = 3, max = 1000, message = "Query must be between 3 and 1000 characters")
    private String query;
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    private Map<String, String> metadata;

    public InvestigationRequest() {}

    public InvestigationRequest(String query, String userId) {
        this.query = query;
        this.userId = userId;
    }

    public InvestigationRequest(String query, String userId, Map<String, String> metadata) {
        this.query = query;
        this.userId = userId;
        this.metadata = metadata;
    }

    // Getters and Setters
    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "InvestigationRequest{" +
                "query='" + query + '\'' +
                ", userId='" + userId + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}