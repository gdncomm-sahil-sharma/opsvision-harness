package com.opsvision.harness.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StructuredChatResponse {
    private int status;
    private boolean success;
    private ChatResponseData data;
    private long timestamp;

    public StructuredChatResponse() {}

    public StructuredChatResponse(int status, boolean success, ChatResponseData data, long timestamp) {
        this.status = status;
        this.success = success;
        this.data = data;
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public ChatResponseData getData() {
        return data;
    }

    public void setData(ChatResponseData data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}