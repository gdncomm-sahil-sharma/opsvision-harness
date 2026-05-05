package com.opsvision.harness.service;

import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.enums.SessionStatus;
import com.opsvision.harness.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;

/**
 * Resolves the active investigation session for a user. A session anchors
 * the chat memory and the audit trail (Conversation rows + ToolExecution
 * rows) for a multi-turn investigation. The harness reuses the most recent
 * ACTIVE session for the user and only creates a new one if none exists,
 * so follow-up questions naturally land in the same session.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    @Autowired
    private SessionRepository sessionRepository;

    public Session getOrCreateActiveSession(String userId, String initialQuery) {
        return sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE)
                .stream()
                .max(Comparator.comparing(Session::getCreatedAt))
                .orElseGet(() -> {
                    Session s = new Session(userId, initialQuery, SessionStatus.ACTIVE);
                    Session saved = sessionRepository.save(s);
                    log.info("Created new session {} for user '{}'", saved.getId(), userId);
                    return saved;
                });
    }
}
