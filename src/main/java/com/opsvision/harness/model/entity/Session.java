package com.opsvision.harness.model.entity;

import com.opsvision.harness.model.enums.SessionStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "session", indexes = {
    @Index(name = "idx_session_user_id", columnList = "user_id"),
    @Index(name = "idx_session_status", columnList = "status"),
    @Index(name = "idx_session_created_at", columnList = "created_at")
})
public class Session {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "initial_query", nullable = false, columnDefinition = "TEXT")
    private String initialQuery;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SessionStatus status;

    /** User-editable display title. NULL means "no explicit title" — the
     *  API derives a display title from {@link #initialQuery} via
     *  {@link #deriveTitle(String)} when this is null. */
    @Column(length = 255)
    private String title;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @ElementCollection
    @CollectionTable(name = "session_metadata", joinColumns = @JoinColumn(name = "session_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Conversation> conversations;

    // Constructors
    public Session() {}

    public Session(String userId, String initialQuery, SessionStatus status) {
        this.userId = userId;
        this.initialQuery = initialQuery;
        this.status = status;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getInitialQuery() {
        return initialQuery;
    }

    public void setInitialQuery(String initialQuery) {
        this.initialQuery = initialQuery;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns this session's display title — the explicit {@code title} if
     * one is set, otherwise a value derived from {@code initialQuery}.
     */
    public String getDisplayTitle() {
        return title != null ? title : deriveTitle(initialQuery);
    }

    /**
     * Build a display title from a raw query: collapse internal whitespace
     * (so multi-line questions don't render with embedded newlines), trim,
     * and truncate to 80 chars (with an ellipsis suffix when truncated).
     */
    public static String deriveTitle(String initialQuery) {
        if (initialQuery == null) return "Untitled chat";
        String collapsed = initialQuery.replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) return "Untitled chat";
        if (collapsed.length() <= 80) return collapsed;
        return collapsed.substring(0, 80) + "…";
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public List<Conversation> getConversations() {
        return conversations;
    }

    public void setConversations(List<Conversation> conversations) {
        this.conversations = conversations;
    }
}