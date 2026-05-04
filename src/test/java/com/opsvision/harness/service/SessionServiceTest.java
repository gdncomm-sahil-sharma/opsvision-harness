package com.opsvision.harness.service;

import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.enums.SessionStatus;
import com.opsvision.harness.repository.SessionRepository;
import com.opsvision.harness.service.context.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @InjectMocks
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        
        // Set configuration values
        ReflectionTestUtils.setField(sessionService, "maxActiveSessionsPerUser", 5);
        ReflectionTestUtils.setField(sessionService, "sessionTtl", Duration.ofHours(4));
    }

    @Test
    void createSession_ShouldCreateNewSession() {
        // Given
        String userId = "testUser";
        String query = "Order 12345 didn't ship";
        
        when(sessionRepository.countActiveSessionsForUser(userId, SessionStatus.ACTIVE)).thenReturn(0L);
        
        Session savedSession = new Session(userId, query, SessionStatus.ACTIVE);
        savedSession.setId(UUID.randomUUID());
        when(sessionRepository.save(any(Session.class))).thenReturn(savedSession);

        // When
        Session result = sessionService.createSession(userId, query);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getInitialQuery()).isEqualTo(query);
        assertThat(result.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        
        verify(sessionRepository).save(any(Session.class));
        verify(valueOperations).set(anyString(), eq(savedSession), any(Duration.class));
        verify(setOperations).add(anyString(), anyString());
    }

    @Test
    void getSession_ShouldReturnCachedSession() {
        // Given
        UUID sessionId = UUID.randomUUID();
        Session cachedSession = new Session("testUser", "test query", SessionStatus.ACTIVE);
        cachedSession.setId(sessionId);
        
        when(valueOperations.get(anyString())).thenReturn(cachedSession);

        // When
        Optional<Session> result = sessionService.getSession(sessionId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(cachedSession);
        
        verify(valueOperations).get(anyString());
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void getSession_ShouldFallbackToDatabase_WhenNotCached() {
        // Given
        UUID sessionId = UUID.randomUUID();
        Session dbSession = new Session("testUser", "test query", SessionStatus.ACTIVE);
        dbSession.setId(sessionId);
        
        when(valueOperations.get(anyString())).thenReturn(null);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(dbSession));

        // When
        Optional<Session> result = sessionService.getSession(sessionId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(dbSession);
        
        verify(valueOperations).get(anyString());
        verify(sessionRepository).findById(sessionId);
        verify(valueOperations).set(anyString(), eq(dbSession), any(Duration.class));
    }

    @Test
    void updateSession_ShouldUpdateStatusAndCache() {
        // Given
        UUID sessionId = UUID.randomUUID();
        Session existingSession = new Session("testUser", "test query", SessionStatus.ACTIVE);
        existingSession.setId(sessionId);
        
        Session updatedSession = new Session("testUser", "test query", SessionStatus.COMPLETED);
        updatedSession.setId(sessionId);
        
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(existingSession));
        when(sessionRepository.save(any(Session.class))).thenReturn(updatedSession);

        // When
        Session result = sessionService.updateSession(sessionId, SessionStatus.COMPLETED);

        // Then
        assertThat(result.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        
        verify(sessionRepository).findById(sessionId);
        verify(sessionRepository).save(any(Session.class));
        verify(valueOperations).set(anyString(), eq(updatedSession), any(Duration.class));
    }
}