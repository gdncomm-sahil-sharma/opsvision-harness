package com.opsvision.harness.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Wrapper for non-streaming chat responses. {@code chatId} is the resolved
 * chat the message landed in (the existing one if the request carried a
 * chatId, or the newly-created one otherwise). Omitted from the wire format
 * when null (e.g. on early failures before a chat could be resolved).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StructuredChatResponse {
    private int status;
    private boolean success;
    private ChatResponseData data;
    private long timestamp;
    private UUID chatId;

    public StructuredChatResponse() {}

    public StructuredChatResponse(int status, boolean success, ChatResponseData data, long timestamp) {
        this(status, success, data, timestamp, null);
    }

    public StructuredChatResponse(int status, boolean success, ChatResponseData data, long timestamp, UUID chatId) {
        this.status = status;
        this.success = success;
        this.data = data;
        this.timestamp = timestamp;
        this.chatId = chatId;
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

    public UUID getChatId() {
        return chatId;
    }

    public void setChatId(UUID chatId) {
        this.chatId = chatId;
    }
}
