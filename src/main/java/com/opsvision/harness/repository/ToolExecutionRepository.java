package com.opsvision.harness.repository;

import com.opsvision.harness.model.entity.ToolExecution;
import com.opsvision.harness.model.enums.ToolExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ToolExecutionRepository extends JpaRepository<ToolExecution, UUID> {
    
    List<ToolExecution> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
    
    List<ToolExecution> findByToolNameAndStatus(String toolName, ToolExecutionStatus status);
    
    @Query("SELECT te FROM ToolExecution te WHERE te.conversation.id = :conversationId AND te.status = :status")
    List<ToolExecution> findByConversationIdAndStatus(@Param("conversationId") UUID conversationId, 
                                                       @Param("status") ToolExecutionStatus status);
    
    @Query("SELECT te FROM ToolExecution te WHERE te.conversation.session.id = :sessionId ORDER BY te.createdAt ASC")
    List<ToolExecution> findBySessionIdOrderByCreatedAt(@Param("sessionId") UUID sessionId);
    
    @Query("SELECT te FROM ToolExecution te WHERE te.status = :status AND te.createdAt < :cutoffTime")
    List<ToolExecution> findStaleExecutions(@Param("status") ToolExecutionStatus status, 
                                            @Param("cutoffTime") LocalDateTime cutoffTime);
    
    @Query("SELECT AVG(te.executionTimeMs) FROM ToolExecution te WHERE te.toolName = :toolName AND te.status = 'SUCCESS'")
    Double findAverageExecutionTimeForTool(@Param("toolName") String toolName);
    
    @Query("SELECT COUNT(te) FROM ToolExecution te WHERE te.toolName = :toolName AND te.status = :status AND te.createdAt >= :since")
    long countToolExecutionsWithStatusSince(@Param("toolName") String toolName, 
                                            @Param("status") ToolExecutionStatus status, 
                                            @Param("since") LocalDateTime since);
}