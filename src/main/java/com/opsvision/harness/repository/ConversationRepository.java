package com.opsvision.harness.repository;

import com.opsvision.harness.model.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    
    List<Conversation> findBySessionIdOrderBySequenceNumberDesc(UUID sessionId);
    
    @Query("SELECT c FROM Conversation c WHERE c.session.id = :sessionId ORDER BY c.sequenceNumber DESC")
    List<Conversation> findBySessionIdOrderBySequenceDesc(@Param("sessionId") UUID sessionId);
    
    @Query("SELECT c FROM Conversation c WHERE c.session.id = :sessionId ORDER BY c.sequenceNumber DESC LIMIT :limit")
    List<Conversation> findLastNConversationsForSession(@Param("sessionId") UUID sessionId, @Param("limit") int limit);
    
    @Query("SELECT MAX(c.sequenceNumber) FROM Conversation c WHERE c.session.id = :sessionId")
    Optional<Integer> findMaxSequenceNumberForSession(@Param("sessionId") UUID sessionId);
    
    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.toolExecutions WHERE c.id = :conversationId")
    Optional<Conversation> findByIdWithToolExecutions(@Param("conversationId") UUID conversationId);
    
    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.toolExecutions WHERE c.session.id = :sessionId ORDER BY c.sequenceNumber ASC")
    List<Conversation> findBySessionIdWithToolExecutions(@Param("sessionId") UUID sessionId);

    @Query("SELECT c FROM Conversation c " +
           "WHERE c.session.id = :sessionId " +
           "  AND c.response IS NOT NULL " +
           "  AND c.response NOT LIKE 'ERROR:%' " +
           "  AND (c.answered IS NULL OR c.answered = true) " +
           "ORDER BY c.sequenceNumber DESC " +
           "LIMIT :limit")
    List<Conversation> findRecentForMemoryReplay(@Param("sessionId") UUID sessionId,
                                                 @Param("limit") int limit);

    /** Oldest-first message history for a chat — used by the message-history endpoint. */
    List<Conversation> findBySessionIdOrderBySequenceNumberAsc(UUID sessionId);

    /** Most recent {@code created_at} across all conversations in a session,
     *  used to power {@code lastMessageAt} on the chat-list endpoint. Null when
     *  the chat has no messages yet (caller falls back to {@code createdAt}). */
    @Query("SELECT MAX(c.createdAt) FROM Conversation c WHERE c.session.id = :sessionId")
    Optional<java.time.LocalDateTime> findLatestCreatedAtForSession(@Param("sessionId") UUID sessionId);
}