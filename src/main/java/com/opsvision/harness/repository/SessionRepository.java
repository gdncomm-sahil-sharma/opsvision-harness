package com.opsvision.harness.repository;

import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {
    
    List<Session> findByUserIdAndStatus(String userId, SessionStatus status);
    
    List<Session> findByUserIdOrderByCreatedAtDesc(String userId);
    
    @Query("SELECT COUNT(s) FROM Session s WHERE s.userId = :userId AND s.status = :status")
    long countActiveSessionsForUser(@Param("userId") String userId, @Param("status") SessionStatus status);
    
    @Query("SELECT s FROM Session s WHERE s.status = :status AND s.updatedAt < :cutoffTime")
    List<Session> findExpiredSessions(@Param("status") SessionStatus status, @Param("cutoffTime") LocalDateTime cutoffTime);
    
    @Modifying
    @Query("UPDATE Session s SET s.status = :newStatus, s.updatedAt = CURRENT_TIMESTAMP WHERE s.id IN :sessionIds")
    int updateSessionStatus(@Param("sessionIds") List<UUID> sessionIds, @Param("newStatus") SessionStatus newStatus);
    
    @Query("SELECT s FROM Session s LEFT JOIN FETCH s.conversations WHERE s.id = :sessionId")
    Optional<Session> findByIdWithConversations(@Param("sessionId") UUID sessionId);
}