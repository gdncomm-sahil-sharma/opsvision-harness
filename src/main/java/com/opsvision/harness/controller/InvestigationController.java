package com.opsvision.harness.controller;

import com.opsvision.harness.model.dto.InvestigationRequest;
import com.opsvision.harness.model.dto.InvestigationResponse;
import com.opsvision.harness.model.dto.SessionContext;
import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.service.agent.AgentService;
import com.opsvision.harness.service.context.ContextService;
import com.opsvision.harness.service.context.SessionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investigations")
@CrossOrigin(origins = "*", maxAge = 3600)
public class InvestigationController {
    
    private static final Logger log = LoggerFactory.getLogger(InvestigationController.class);
    
    @Autowired
    private AgentService agentService;
    
    @Autowired
    private SessionService sessionService;
    
    @Autowired
    private ContextService contextService;

    @PostMapping
    public ResponseEntity<InvestigationResponse> startInvestigation(
            @Valid @RequestBody InvestigationRequest request) {
        
        log.info("Starting new investigation for user: {} with query: {}", 
                request.getUserId(), request.getQuery());
        
        try {
            InvestigationResponse response = agentService.investigate(request, null);
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
        } catch (Exception e) {
            log.error("Failed to start investigation: {}", e.getMessage(), e);
            
            InvestigationResponse errorResponse = new InvestigationResponse(
                null, 
                "Failed to start investigation: " + e.getMessage(), 
                false
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/{sessionId}/continue")
    public ResponseEntity<InvestigationResponse> continueInvestigation(
            @PathVariable UUID sessionId,
            @RequestParam String query,
            @RequestParam String userId) {
        
        log.info("Continuing investigation for session: {} with query: {}", sessionId, query);
        
        try {
            InvestigationResponse response = agentService.continueInvestigation(sessionId, query, userId);
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for session {}: {}", sessionId, e.getMessage());
            
            InvestigationResponse errorResponse = new InvestigationResponse(
                sessionId, 
                e.getMessage(), 
                false
            );
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            
        } catch (Exception e) {
            log.error("Failed to continue investigation for session {}: {}", sessionId, e.getMessage(), e);
            
            InvestigationResponse errorResponse = new InvestigationResponse(
                sessionId, 
                "Failed to continue investigation: " + e.getMessage(), 
                false
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionDetailsResponse> getSessionDetails(
            @PathVariable UUID sessionId,
            @RequestParam String userId) {
        
        log.debug("Retrieving session details for: {}", sessionId);
        
        try {
            Optional<Session> sessionOpt = sessionService.getSession(sessionId);
            
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Session session = sessionOpt.get();
            
            if (!session.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            SessionContext context = contextService.getSessionContext(sessionId);
            SessionDetailsResponse response = new SessionDetailsResponse(session, context);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to retrieve session details for {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SessionSummary>> getUserSessions(@PathVariable String userId) {
        log.debug("Retrieving sessions for user: {}", userId);
        
        try {
            List<Session> sessions = sessionService.getUserSessions(userId);
            List<SessionSummary> summaries = sessions.stream()
                .map(SessionSummary::fromSession)
                .toList();
                
            return ResponseEntity.ok(summaries);
            
        } catch (Exception e) {
            log.error("Failed to retrieve sessions for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable UUID sessionId,
            @RequestParam String userId) {
        
        log.info("Deleting session: {} for user: {}", sessionId, userId);
        
        try {
            Optional<Session> sessionOpt = sessionService.getSession(sessionId);
            
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Session session = sessionOpt.get();
            
            if (!session.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Mark session as expired instead of hard delete
            sessionService.updateSession(sessionId, 
                com.opsvision.harness.model.enums.SessionStatus.EXPIRED);
            
            return ResponseEntity.noContent().build();
            
        } catch (Exception e) {
            log.error("Failed to delete session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Inner classes for response DTOs
    public static class SessionDetailsResponse {
        private UUID sessionId;
        private String userId;
        private String initialQuery;
        private String status;
        private String createdAt;
        private String updatedAt;
        private List<SessionContext.ConversationSummary> conversations;
        private int toolResultsCount;
        private long successfulToolsCount;

        public SessionDetailsResponse(Session session, SessionContext context) {
            this.sessionId = session.getId();
            this.userId = session.getUserId();
            this.initialQuery = session.getInitialQuery();
            this.status = session.getStatus().name();
            this.createdAt = session.getCreatedAt().toString();
            this.updatedAt = session.getUpdatedAt() != null ? session.getUpdatedAt().toString() : null;
            this.conversations = context.getConversationHistory();
            this.toolResultsCount = context.getToolResults() != null ? context.getToolResults().size() : 0;
            this.successfulToolsCount = context.getToolResults() != null ? 
                context.getToolResults().stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum() : 0;
        }

        // Getters
        public UUID getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public String getInitialQuery() { return initialQuery; }
        public String getStatus() { return status; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public List<SessionContext.ConversationSummary> getConversations() { return conversations; }
        public int getToolResultsCount() { return toolResultsCount; }
        public long getSuccessfulToolsCount() { return successfulToolsCount; }
    }

    public static class SessionSummary {
        private UUID sessionId;
        private String initialQuery;
        private String status;
        private String createdAt;
        private int conversationCount;

        public static SessionSummary fromSession(Session session) {
            SessionSummary summary = new SessionSummary();
            summary.sessionId = session.getId();
            summary.initialQuery = session.getInitialQuery();
            summary.status = session.getStatus().name();
            summary.createdAt = session.getCreatedAt().toString();
            summary.conversationCount = session.getConversations() != null ? 
                session.getConversations().size() : 0;
            return summary;
        }

        // Getters
        public UUID getSessionId() { return sessionId; }
        public String getInitialQuery() { return initialQuery; }
        public String getStatus() { return status; }
        public String getCreatedAt() { return createdAt; }
        public int getConversationCount() { return conversationCount; }
    }
}