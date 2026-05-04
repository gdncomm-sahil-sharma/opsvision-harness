package com.opsvision.harness.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.harness.model.dto.InvestigationRequest;
import com.opsvision.harness.model.dto.InvestigationResponse;
import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.enums.SessionStatus;
import com.opsvision.harness.repository.ConversationRepository;
import com.opsvision.harness.repository.SessionRepository;
import com.opsvision.harness.repository.ToolExecutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FullInvestigationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SessionRepository sessionRepository;

    @MockBean
    private ConversationRepository conversationRepository;

    @MockBean
    private ToolExecutionRepository toolExecutionRepository;

    @Test
    void fullInvestigationFlow_ShouldWorkEndToEnd() throws Exception {
        // Step 1: Create a new investigation
        InvestigationRequest request = new InvestigationRequest("Order 12345 didn't ship", "testUser");
        
        Session mockSession = new Session("testUser", "Order 12345 didn't ship", SessionStatus.ACTIVE);
        mockSession.setId(UUID.randomUUID());
        
        when(sessionRepository.countActiveSessionsForUser("testUser", SessionStatus.ACTIVE))
            .thenReturn(0L);
        when(sessionRepository.save(any(Session.class)))
            .thenReturn(mockSession);
        when(conversationRepository.findMaxSequenceNumberForSession(any()))
            .thenReturn(Optional.of(0));
        when(conversationRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Start investigation
        MvcResult investigationResult = mockMvc.perform(post("/api/v1/investigations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sessionId").exists())
                .andReturn();

        String responseJson = investigationResult.getResponse().getContentAsString();
        InvestigationResponse investigationResponse = objectMapper.readValue(responseJson, InvestigationResponse.class);
        UUID sessionId = investigationResponse.getSessionId();

        assertThat(sessionId).isNotNull();
        assertThat(investigationResponse.isSuccess()).isTrue();

        // Step 2: Get session details
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(mockSession));

        mockMvc.perform(get("/api/v1/investigations/{sessionId}", sessionId)
                .param("userId", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.userId").value("testUser"))
                .andExpect(jsonPath("$.initialQuery").value("Order 12345 didn't ship"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Step 3: Continue the investigation with a follow-up
        mockMvc.perform(post("/api/v1/investigations/{sessionId}/continue", sessionId)
                .param("query", "What specific steps should we take to resolve this?")
                .param("userId", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()));

        // Step 4: Get user's sessions
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc("testUser"))
            .thenReturn(java.util.List.of(mockSession));

        mockMvc.perform(get("/api/v1/investigations/user/{userId}", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$[0].initialQuery").value("Order 12345 didn't ship"));
    }

    @Test
    void healthEndpoints_ShouldReturnCorrectStatus() throws Exception {
        // Test health endpoint
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("opsvision-harness"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.timestamp").exists());

        // Test readiness endpoint  
        mockMvc.perform(get("/api/v1/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        // Test liveness endpoint
        mockMvc.perform(get("/api/v1/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALIVE"));
    }

    @Test
    void errorHandling_ShouldReturnProperErrorResponses() throws Exception {
        // Test validation error
        InvestigationRequest invalidRequest = new InvestigationRequest("", "");

        mockMvc.perform(post("/api/v1/investigations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.timestamp").exists());

        // Test not found error
        UUID nonExistentSessionId = UUID.randomUUID();
        when(sessionRepository.findById(nonExistentSessionId))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/investigations/{sessionId}", nonExistentSessionId)
                .param("userId", "testUser"))
                .andExpect(status().isNotFound());
    }
}