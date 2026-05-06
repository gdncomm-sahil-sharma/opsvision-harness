package com.opsvision.harness.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversation", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "sequence_number"}),
       indexes = {
           @Index(name = "idx_conversation_session_id", columnList = "session_id"),
           @Index(name = "idx_conversation_sequence", columnList = "session_id, sequence_number")
       })
public class Conversation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;
    
    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;
    
    @Column(columnDefinition = "TEXT")
    private String response;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_data", columnDefinition = "jsonb")
    private JsonNode contextData;

    /** LLM self-grade: did this turn actually answer the user's question?
     *  Null = not graded yet (legacy rows or LLM forgot). Memory replay
     *  filters out only explicit {@code false}. */
    @Column(name = "answered")
    private Boolean answered;

    /** When {@link #answered} is {@code false}, a short phrase describing
     *  the gap (e.g. "no tool indexes by picker_id"). Null otherwise. */
    @Column(name = "unanswered_reason", columnDefinition = "TEXT")
    private String unansweredReason;

    /** User-submitted thumbs-up ({@code true}) / thumbs-down ({@code false}).
     *  Null when no feedback has been submitted yet. */
    @Column(name = "helpful")
    private Boolean helpful;

    /** Optional free-text comment paired with {@link #helpful}. Capped to
     *  2000 chars by the service before persistence. Null if user gave
     *  feedback without a comment, or no feedback at all. */
    @Column(name = "feedback_comment", columnDefinition = "TEXT")
    private String feedbackComment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ToolExecution> toolExecutions;

    // Constructors
    public Conversation() {}

    public Conversation(Session session, Integer sequenceNumber, String query) {
        this.session = session;
        this.sequenceNumber = sequenceNumber;
        this.query = query;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
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

    public JsonNode getContextData() {
        return contextData;
    }

    public void setContextData(JsonNode contextData) {
        this.contextData = contextData;
    }

    public Boolean getAnswered() {
        return answered;
    }

    public void setAnswered(Boolean answered) {
        this.answered = answered;
    }

    public String getUnansweredReason() {
        return unansweredReason;
    }

    public void setUnansweredReason(String unansweredReason) {
        this.unansweredReason = unansweredReason;
    }

    public Boolean getHelpful() {
        return helpful;
    }

    public void setHelpful(Boolean helpful) {
        this.helpful = helpful;
    }

    public String getFeedbackComment() {
        return feedbackComment;
    }

    public void setFeedbackComment(String feedbackComment) {
        this.feedbackComment = feedbackComment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<ToolExecution> getToolExecutions() {
        return toolExecutions;
    }

    public void setToolExecutions(List<ToolExecution> toolExecutions) {
        this.toolExecutions = toolExecutions;
    }
}