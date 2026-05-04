package com.opsvision.harness.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.harness.model.dto.InvestigationRequest;
import com.opsvision.harness.model.dto.InvestigationResponse;
import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.enums.SessionStatus;
import com.opsvision.harness.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
class InvestigationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SessionRepository sessionRepository;

    private InvestigationRequest testRequest;
    private Session testSession;

    @BeforeEach
    void setUp() {
        testRequest = new InvestigationRequest("Order 12345 didn't ship", "testUser");
        
        testSession = new Session("testUser", "Order 12345 didn't ship", SessionStatus.ACTIVE);
        testSession.setId(UUID.randomUUID());
    }

    @Test
    void startInvestigation_ShouldReturnSuccessResponse() throws Exception {
        // Given
        when(sessionRepository.countActiveSessionsForUser("testUser", SessionStatus.ACTIVE))
            .thenReturn(0L);
        when(sessionRepository.save(any(Session.class)))
            .thenReturn(testSession);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/v1/investigations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sessionId").exists())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        InvestigationResponse response = objectMapper.readValue(responseJson, InvestigationResponse.class);
        
        assertThat(response.getSessionId()).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResponse()).isNotEmpty();
    }

    @Test
    void startInvestigation_ShouldReturnValidationError_WhenRequestInvalid() throws Exception {
        // Given
        InvestigationRequest invalidRequest = new InvestigationRequest("", ""); // Empty fields

        // When & Then
        mockMvc.perform(post("/api/v1/investigations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    void getSessionDetails_ShouldReturnSessionInfo() throws Exception {
        // Given
        UUID sessionId = testSession.getId();
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(testSession));

        // When & Then
        mockMvc.perform(get("/api/v1/investigations/{sessionId}", sessionId)
                .param("userId", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.userId").value("testUser"))
                .andExpect(jsonPath("$.initialQuery").value("Order 12345 didn't ship"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getSessionDetails_ShouldReturn403_WhenUserNotAuthorized() throws Exception {
        // Given
        UUID sessionId = testSession.getId();
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(testSession));

        // When & Then
        mockMvc.perform(get("/api/v1/investigations/{sessionId}", sessionId)
                .param("userId", "differentUser"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSessionDetails_ShouldReturn404_WhenSessionNotFound() throws Exception {
        // Given
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/investigations/{sessionId}", sessionId)
                .param("userId", "testUser"))
                .andExpect(status().isNotFound());
    }

    @Test
    void continueInvestigation_ShouldReturnFollowUpResponse() throws Exception {
        // Given
        UUID sessionId = testSession.getId();
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(testSession));

        // When & Then
        mockMvc.perform(post("/api/v1/investigations/{sessionId}/continue", sessionId)
                .param("query", "What can we do to fix this?")
                .param("userId", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()));
    }

    @Test
    void deleteSession_ShouldMarkSessionAsExpired() throws Exception {
        // Given
        UUID sessionId = testSession.getId();
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(Session.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When & Then
        mockMvc.perform(delete("/api/v1/investigations/{sessionId}", sessionId)
                .param("userId", "testUser"))
                .andExpect(status().isNoContent());
    }
}