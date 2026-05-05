package com.opsvision.harness.model.dto;

import java.util.List;

public class ChatResponse {
    private String response;
    private boolean success;
    private String error;
    private List<String> toolsUsed;

    public ChatResponse() {}

    public ChatResponse(String response, boolean success) {
        this.response = response;
        this.success = success;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<String> getToolsUsed() {
        return toolsUsed;
    }

    public void setToolsUsed(List<String> toolsUsed) {
        this.toolsUsed = toolsUsed;
    }
}