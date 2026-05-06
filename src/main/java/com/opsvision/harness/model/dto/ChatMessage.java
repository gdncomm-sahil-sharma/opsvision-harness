package com.opsvision.harness.model.dto;

import java.util.UUID;

/**
 * Inbound message body for {@code POST /api/chat} and
 * {@code POST /api/chat/stream}. {@code chatId} is optional — when null
 * or absent, the BE creates a new chat and returns its id in the response.
 * When present, the message lands in that existing chat (which must be
 * owned by the same {@code userId} and currently {@code ACTIVE}).
 */
public class ChatMessage {
    private String userId;
    private UUID chatId;
    private String message;

    public ChatMessage() {}

    public ChatMessage(String userId, String message) {
        this.userId = userId;
        this.message = message;
    }

    public ChatMessage(String userId, UUID chatId, String message) {
        this.userId = userId;
        this.chatId = chatId;
        this.message = message;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public UUID getChatId() {
        return chatId;
    }

    public void setChatId(UUID chatId) {
        this.chatId = chatId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
