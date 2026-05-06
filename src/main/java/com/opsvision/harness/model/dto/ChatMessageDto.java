package com.opsvision.harness.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * One turn within a chat, surfaced by
 * {@code GET /api/chats/{chatId}/messages}. {@code response} may be null
 * for a turn that errored mid-flight or whose stream is still in progress.
 * {@code references} is the parsed {@code Conversation.contextData} JSONB
 * (the identifiers map populated by {@link com.opsvision.harness.model.dto.response.ChatResponseData#getReferences()})
 * or null when no references were emitted.
 *
 * <p>{@code answered} / {@code unansweredReason} carry the LLM's self-grade
 * for that turn (null on legacy or ungraded rows). {@code helpful} /
 * {@code feedbackComment} carry user-submitted feedback (null when none was
 * given). All four are omitted from the JSON wire format when null via
 * the class-level {@link JsonInclude}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageDto {
    private int sequence;
    private String query;
    private String response;
    private JsonNode references;
    private LocalDateTime createdAt;
    private Boolean answered;
    private String unansweredReason;
    private Boolean helpful;
    private String feedbackComment;

    public ChatMessageDto() {}

    public ChatMessageDto(int sequence, String query, String response,
                          JsonNode references, LocalDateTime createdAt,
                          Boolean answered, String unansweredReason,
                          Boolean helpful, String feedbackComment) {
        this.sequence = sequence;
        this.query = query;
        this.response = response;
        this.references = references;
        this.createdAt = createdAt;
        this.answered = answered;
        this.unansweredReason = unansweredReason;
        this.helpful = helpful;
        this.feedbackComment = feedbackComment;
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

    public Boolean getAnswered() { return answered; }
    public void setAnswered(Boolean answered) { this.answered = answered; }

    public String getUnansweredReason() { return unansweredReason; }
    public void setUnansweredReason(String unansweredReason) { this.unansweredReason = unansweredReason; }

    public Boolean getHelpful() { return helpful; }
    public void setHelpful(Boolean helpful) { this.helpful = helpful; }

    public String getFeedbackComment() { return feedbackComment; }
    public void setFeedbackComment(String feedbackComment) { this.feedbackComment = feedbackComment; }
}
