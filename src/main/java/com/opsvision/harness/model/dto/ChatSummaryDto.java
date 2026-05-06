package com.opsvision.harness.model.dto;

import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.enums.SessionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sidebar/list view of a chat. {@code title} is the explicit title if set,
 * otherwise derived from the chat's first message via
 * {@link Session#getDisplayTitle()}. {@code lastMessageAt} is the timestamp
 * of the most recent {@code Conversation} row in this chat (falls back to
 * {@code createdAt} if the chat has no messages yet).
 */
public class ChatSummaryDto {
    private UUID chatId;
    private String title;
    private SessionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;

    public ChatSummaryDto() {}

    public ChatSummaryDto(UUID chatId, String title, SessionStatus status,
                          LocalDateTime createdAt, LocalDateTime lastMessageAt) {
        this.chatId = chatId;
        this.title = title;
        this.status = status;
        this.createdAt = createdAt;
        this.lastMessageAt = lastMessageAt;
    }

    public UUID getChatId() { return chatId; }
    public void setChatId(UUID chatId) { this.chatId = chatId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }
}
