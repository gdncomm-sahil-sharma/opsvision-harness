package com.opsvision.harness.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

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
    /**
     * Full structured response (textResponse, timelines, table, references,
     * answered, unansweredReason) — same shape the UI received when the
     * turn was live. Null on error rows and on streaming turns whose
     * final parse failed; clients should fall back to {@link #response}
     * (summary) and {@link #references} in those cases.
     *
     * <p>Typed as {@link Object} (a {@code Map<String, Object>} at runtime)
     * rather than {@code JsonNode} so the response serialiser doesn't
     * matter — Spring Boot 4.0.6 ships {@code tools.jackson.databind} 3.x
     * for HTTP response serialisation, which doesn't recognise the 2.x
     * {@code com.fasterxml.jackson.databind.JsonNode} as a JSON tree and
     * falls back to bean introspection (emitting bean properties like
     * {@code array:false, bigDecimal:false, ...} instead of the actual
     * tree). Converting to a plain {@code Map} at the controller
     * boundary sidesteps the version mismatch.
     */
    private Object responseData;
    private Object references;
    private LocalDateTime createdAt;
    private Boolean answered;
    private String unansweredReason;
    private Boolean helpful;
    private String feedbackComment;

    public ChatMessageDto() {}

    public ChatMessageDto(int sequence, String query, String response,
                          Object responseData,
                          Object references, LocalDateTime createdAt,
                          Boolean answered, String unansweredReason,
                          Boolean helpful, String feedbackComment) {
        this.sequence = sequence;
        this.query = query;
        this.response = response;
        this.responseData = responseData;
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

    public Object getResponseData() { return responseData; }
    public void setResponseData(Object responseData) { this.responseData = responseData; }

    public Object getReferences() { return references; }
    public void setReferences(Object references) { this.references = references; }

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
