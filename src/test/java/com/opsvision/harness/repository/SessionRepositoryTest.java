package com.opsvision.harness.repository;

import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.enums.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SessionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SessionRepository sessionRepository;

    @Test
    void findByUserIdAndStatus_ShouldReturnCorrectSessions() {
        // Given
        String userId = "testUser";
        Session activeSession = new Session(userId, "Query 1", SessionStatus.ACTIVE);
        Session completedSession = new Session(userId, "Query 2", SessionStatus.COMPLETED);
        Session anotherUserSession = new Session("anotherUser", "Query 3", SessionStatus.ACTIVE);

        entityManager.persist(activeSession);
        entityManager.persist(completedSession);
        entityManager.persist(anotherUserSession);
        entityManager.flush();

        // When
        List<Session> activeSessions = sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE);
        List<Session> completedSessions = sessionRepository.findByUserIdAndStatus(userId, SessionStatus.COMPLETED);

        // Then
        assertThat(activeSessions).hasSize(1);
        assertThat(activeSessions.get(0).getInitialQuery()).isEqualTo("Query 1");
        
        assertThat(completedSessions).hasSize(1);
        assertThat(completedSessions.get(0).getInitialQuery()).isEqualTo("Query 2");
    }

    @Test
    void countActiveSessionsForUser_ShouldReturnCorrectCount() {
        // Given
        String userId = "testUser";
        Session session1 = new Session(userId, "Query 1", SessionStatus.ACTIVE);
        Session session2 = new Session(userId, "Query 2", SessionStatus.ACTIVE);
        Session expiredSession = new Session(userId, "Query 3", SessionStatus.EXPIRED);

        entityManager.persist(session1);
        entityManager.persist(session2);
        entityManager.persist(expiredSession);
        entityManager.flush();

        // When
        long activeCount = sessionRepository.countActiveSessionsForUser(userId, SessionStatus.ACTIVE);

        // Then
        assertThat(activeCount).isEqualTo(2);
    }

    @Test
    void findExpiredSessions_ShouldReturnOldActiveSessions() {
        // Given
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(1);
        
        Session oldSession = new Session("user1", "Old query", SessionStatus.ACTIVE);
        oldSession.setUpdatedAt(cutoffTime.minusMinutes(30));
        
        Session recentSession = new Session("user2", "Recent query", SessionStatus.ACTIVE);
        recentSession.setUpdatedAt(LocalDateTime.now());

        entityManager.persist(oldSession);
        entityManager.persist(recentSession);
        entityManager.flush();

        // When
        List<Session> expiredSessions = sessionRepository.findExpiredSessions(SessionStatus.ACTIVE, cutoffTime);

        // Then
        assertThat(expiredSessions).hasSize(1);
        assertThat(expiredSessions.get(0).getInitialQuery()).isEqualTo("Old query");
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_ShouldReturnSessionsInDescendingOrder() {
        // Given
        String userId = "testUser";
        Session oldSession = new Session(userId, "Old query", SessionStatus.COMPLETED);
        Session newSession = new Session(userId, "New query", SessionStatus.ACTIVE);

        // Persist old session first
        entityManager.persist(oldSession);
        entityManager.flush();
        
        // Wait a bit and persist new session
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        entityManager.persist(newSession);
        entityManager.flush();

        // When
        List<Session> sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // Then
        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).getInitialQuery()).isEqualTo("New query");
        assertThat(sessions.get(1).getInitialQuery()).isEqualTo("Old query");
    }

    @Test
    void save_ShouldPersistSession() {
        // Given
        Session session = new Session("testUser", "Test query", SessionStatus.ACTIVE);

        // When
        Session savedSession = sessionRepository.save(session);

        // Then
        assertThat(savedSession.getId()).isNotNull();
        assertThat(savedSession.getCreatedAt()).isNotNull();
        assertThat(savedSession.getUpdatedAt()).isNotNull();

        Optional<Session> foundSession = sessionRepository.findById(savedSession.getId());
        assertThat(foundSession).isPresent();
        assertThat(foundSession.get().getUserId()).isEqualTo("testUser");
        assertThat(foundSession.get().getInitialQuery()).isEqualTo("Test query");
        assertThat(foundSession.get().getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }
}