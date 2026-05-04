package com.opsvision.harness.service;

import com.opsvision.harness.model.dto.InvestigationRequest;
import com.opsvision.harness.model.dto.InvestigationResponse;
import com.opsvision.harness.model.dto.SessionContext;
import com.opsvision.harness.model.dto.ToolResult;
import com.opsvision.harness.model.entity.Conversation;
import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.enums.SessionStatus;
import com.opsvision.harness.model.enums.ToolExecutionStatus;
import com.opsvision.harness.model.enums.ToolType;
import com.opsvision.harness.repository.ConversationRepository;
import com.opsvision.harness.service.agent.AgentService;
import com.opsvision.harness.service.ai.ChatService;
import com.opsvision.harness.service.context.ContextService;
import com.opsvision.harness.service.context.SessionService;
import com.opsvision.harness.service.mcp.McpClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private ContextService contextService;

    @Mock
    private McpClientService mcpClientService;

    @Mock
    private ChatService chatService;

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private AgentService agentService;

    private InvestigationRequest testRequest;
    private Session testSession;
    private List<ToolResult> testToolResults;

    @BeforeEach
    void setUp() {
        testRequest = new InvestigationRequest("Order 12345 didn't ship", "testUser");
        
        testSession = new Session("testUser", "Order 12345 didn't ship", SessionStatus.ACTIVE);
        testSession.setId(UUID.randomUUID());
        
        ToolResult successfulResult = new ToolResult(ToolType.GET_ORDER_TIMELINE, 
            java.util.Map.of("order_id", "12345"));
        successfulResult.setStatus(ToolExecutionStatus.SUCCESS);
        successfulResult.setExecutionTimeMs(500);
        
        testToolResults = List.of(successfulResult);
    }

    @Test
    void investigate_ShouldReturnSuccessfulResponse() {
        // Given
        SessionContext mockContext = new SessionContext(testSession.getId(), "testUser", "Order 12345 didn't ship");
        Conversation mockConversation = new Conversation(testSession, 1, testRequest.getQuery());
        mockConversation.setId(UUID.randomUUID());
        
        when(sessionService.createSession(testRequest.getUserId(), testRequest.getQuery()))
            .thenReturn(testSession);
        when(conversationRepository.findMaxSequenceNumberForSession(testSession.getId()))
            .thenReturn(Optional.of(0));
        when(conversationRepository.save(any(Conversation.class)))
            .thenReturn(mockConversation);
        when(mcpClientService.invokeTools(any(), eq("12345")))
            .thenReturn(testToolResults);
        when(contextService.buildContext(testSession.getId(), testToolResults))
            .thenReturn(mockContext);
        when(chatService.generateInvestigation(mockContext, testRequest.getQuery()))
            .thenReturn("AI generated investigation response");
        when(sessionService.updateSession(testSession.getId(), SessionStatus.COMPLETED))
            .thenReturn(testSession);

        // When
        InvestigationResponse response = agentService.investigate(testRequest, null);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getSessionId()).isEqualTo(testSession.getId());
        assertThat(response.getResponse()).isEqualTo("AI generated investigation response");
        assertThat(response.getToolResults()).hasSize(1);
        
        verify(sessionService).createSession(testRequest.getUserId(), testRequest.getQuery());
        verify(mcpClientService).invokeTools(any(), eq("12345"));
        verify(contextService).buildContext(testSession.getId(), testToolResults);
        verify(chatService).generateInvestigation(mockContext, testRequest.getQuery());
    }

    @Test
    void selectTools_ShouldSelectCorrectToolsForShippingQuery() {
        // Given
        String shippingQuery = "Why hasn't order 12345 shipped yet?";

        // When
        List<ToolType> selectedTools = agentService.selectTools(shippingQuery);

        // Then
        assertThat(selectedTools).containsExactlyInAnyOrder(
            ToolType.GET_ORDER_TIMELINE,
            ToolType.GET_TASK_HISTORY,
            ToolType.GET_AUDIT_EVENTS
        );
    }

    @Test
    void selectTools_ShouldSelectCorrectToolsForInventoryQuery() {
        // Given
        String inventoryQuery = "Check inventory levels for order 12345";

        // When
        List<ToolType> selectedTools = agentService.selectTools(inventoryQuery);

        // Then
        assertThat(selectedTools).containsExactlyInAnyOrder(
            ToolType.GET_INVENTORY_HISTORY,
            ToolType.GET_ORDER_TIMELINE,
            ToolType.GET_AUDIT_EVENTS
        );
    }

    @Test
    void selectTools_ShouldSelectAllToolsForGeneralQuery() {
        // Given
        String generalQuery = "What happened with order 12345?";

        // When
        List<ToolType> selectedTools = agentService.selectTools(generalQuery);

        // Then
        assertThat(selectedTools).containsExactlyInAnyOrder(
            ToolType.GET_ORDER_TIMELINE,
            ToolType.GET_TASK_HISTORY,
            ToolType.GET_INVENTORY_HISTORY,
            ToolType.GET_AUDIT_EVENTS
        );
    }

    @Test
    void continueInvestigation_ShouldReturnFollowUpResponse() {
        // Given
        UUID sessionId = testSession.getId();
        String followUpQuery = "What can we do to fix this?";
        SessionContext mockContext = new SessionContext(sessionId, "testUser", "Order 12345 didn't ship");
        Conversation mockConversation = new Conversation(testSession, 2, followUpQuery);
        
        when(sessionService.getSession(sessionId)).thenReturn(Optional.of(testSession));
        when(conversationRepository.findMaxSequenceNumberForSession(sessionId))
            .thenReturn(Optional.of(1));
        when(conversationRepository.save(any(Conversation.class)))
            .thenReturn(mockConversation);
        when(contextService.getSessionContext(sessionId)).thenReturn(mockContext);
        when(chatService.generateFollowUpResponse(mockContext, followUpQuery))
            .thenReturn("AI generated follow-up response");

        // When
        InvestigationResponse response = agentService.continueInvestigation(sessionId, followUpQuery, "testUser");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getResponse()).isEqualTo("AI generated follow-up response");
        
        verify(sessionService).getSession(sessionId);
        verify(contextService).getSessionContext(sessionId);
        verify(chatService).generateFollowUpResponse(mockContext, followUpQuery);
    }

    @Test
    void investigate_ShouldHandleFailureGracefully() {
        // Given
        when(sessionService.createSession(testRequest.getUserId(), testRequest.getQuery()))
            .thenThrow(new RuntimeException("Database error"));

        // When
        InvestigationResponse response = agentService.investigate(testRequest, null);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getResponse()).contains("Investigation failed");
        assertThat(response.getToolResults()).isEmpty();
    }
}