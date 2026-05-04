package com.opsvision.harness.service.context;

import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.enums.SessionStatus;
import com.opsvision.harness.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SessionService {
    
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final String ACTIVE_SESSIONS_KEY = "user:%s:active:sessions";
    private static final String SESSION_CACHE_KEY = "session:%s";
    
    @Autowired
    private SessionRepository sessionRepository;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Value("${app.session.max-active-per-user:5}")
    private int maxActiveSessionsPerUser;
    
    @Value("${app.session.default-ttl:4h}")
    private Duration sessionTtl;

    public Session createSession(String userId, String query) {
        log.info("Creating new session for user: {}", userId);
        
        // Check if user has too many active sessions
        long activeCount = sessionRepository.countActiveSessionsForUser(userId, SessionStatus.ACTIVE);
        if (activeCount >= maxActiveSessionsPerUser) {
            log.warn("User {} has {} active sessions, cleaning up oldest", userId, activeCount);
            cleanupOldestActiveSession(userId);
        }
        
        Session session = new Session(userId, query, SessionStatus.ACTIVE);
        session.setMetadata(new HashMap<>());
        session = sessionRepository.save(session);
        
        // Cache session
        cacheSession(session);
        
        // Add to user's active sessions
        String activeSessionsKey = String.format(ACTIVE_SESSIONS_KEY, userId);
        redisTemplate.opsForSet().add(activeSessionsKey, session.getId().toString());
        redisTemplate.expire(activeSessionsKey, Duration.ofHours(24));
        
        log.info("Created session {} for user {}", session.getId(), userId);
        return session;
    }

    public Optional<Session> getSession(UUID sessionId) {
        log.debug("Retrieving session: {}", sessionId);
        
        // Try cache first
        Session cachedSession = getCachedSession(sessionId);
        if (cachedSession != null) {
            return Optional.of(cachedSession);
        }
        
        // Fallback to database
        Optional<Session> session = sessionRepository.findById(sessionId);
        if (session.isPresent()) {
            cacheSession(session.get());
        }
        
        return session;
    }

    public Optional<Session> getSessionWithConversations(UUID sessionId) {
        log.debug("Retrieving session with conversations: {}", sessionId);
        
        Optional<Session> session = sessionRepository.findByIdWithConversations(sessionId);
        if (session.isPresent()) {
            cacheSession(session.get());
        }
        
        return session;
    }

    public Session updateSession(UUID sessionId, SessionStatus status) {
        log.debug("Updating session {} status to: {}", sessionId, status);
        
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
            
        session.setStatus(status);
        session = sessionRepository.save(session);
        
        // Update cache
        cacheSession(session);
        
        // Remove from active sessions if completed/failed
        if (status == SessionStatus.COMPLETED || status == SessionStatus.FAILED) {
            removeFromActiveSessionsCache(session.getUserId(), sessionId);
        }
        
        log.info("Updated session {} status to: {}", sessionId, status);
        return session;
    }

    public List<Session> getUserActiveSessions(String userId) {
        return sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE);
    }

    public List<Session> getUserSessions(String userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void cleanupExpiredSessions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minus(sessionTtl);
        List<Session> expiredSessions = sessionRepository.findExpiredSessions(SessionStatus.ACTIVE, cutoffTime);
        
        if (!expiredSessions.isEmpty()) {
            log.info("Found {} expired sessions, marking as expired", expiredSessions.size());
            
            List<UUID> sessionIds = expiredSessions.stream()
                .map(Session::getId)
                .toList();
                
            sessionRepository.updateSessionStatus(sessionIds, SessionStatus.EXPIRED);
            
            // Remove from caches
            expiredSessions.forEach(session -> {
                removeCachedSession(session.getId());
                removeFromActiveSessionsCache(session.getUserId(), session.getId());
            });
        }
    }

    private void cleanupOldestActiveSession(String userId) {
        List<Session> activeSessions = sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE);
        if (!activeSessions.isEmpty()) {
            Session oldest = activeSessions.stream()
                .min((s1, s2) -> s1.getCreatedAt().compareTo(s2.getCreatedAt()))
                .orElse(null);
                
            if (oldest != null) {
                updateSession(oldest.getId(), SessionStatus.EXPIRED);
                log.info("Expired oldest session {} for user {}", oldest.getId(), userId);
            }
        }
    }

    private void cacheSession(Session session) {
        String cacheKey = String.format(SESSION_CACHE_KEY, session.getId());
        redisTemplate.opsForValue().set(cacheKey, session, sessionTtl);
    }

    private Session getCachedSession(UUID sessionId) {
        String cacheKey = String.format(SESSION_CACHE_KEY, sessionId);
        return (Session) redisTemplate.opsForValue().get(cacheKey);
    }

    private void removeCachedSession(UUID sessionId) {
        String cacheKey = String.format(SESSION_CACHE_KEY, sessionId);
        redisTemplate.delete(cacheKey);
    }

    private void removeFromActiveSessionsCache(String userId, UUID sessionId) {
        String activeSessionsKey = String.format(ACTIVE_SESSIONS_KEY, userId);
        redisTemplate.opsForSet().remove(activeSessionsKey, sessionId.toString());
    }
}