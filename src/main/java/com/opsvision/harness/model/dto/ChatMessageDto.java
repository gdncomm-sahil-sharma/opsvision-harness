package com.opsvision.harness.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * One turn within a chat, surfaced by
 * {@code GET /api/chats/{chatId}/messages}. {@code response} may be null
 * for a turn that errored mid-flight or whose stream is still in progress.
 * {@code references} is the parsed {@code Conversation.contextData} JSONB
 * (the identifiers map populated by {@link com.opsvision.harness.model.dto.response.ChatResponseData#getReferences()})
 * or null when no references were emitted.
 */
public class ChatMessageDto {
    private int sequence;
    private String query;
    private String response;
    private JsonNode references;
    private LocalDateTime createdAt;

    public ChatMessageDto() {}

    public ChatMessageDto(int sequence, String query, String response,
                          JsonNode references, LocalDateTime createdAt) {
        this.sequence = sequence;
        this.query = query;
        this.response = response;
        this.references = references;
        this.createdAt = createdAt;
    }

    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public JsonNode getReferences() { return references; }
    public void setReferences(JsonNode references) { this.references = references; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
