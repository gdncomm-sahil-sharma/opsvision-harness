package com.opsvision.harness.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.harness.model.dto.InvestigationRequest;
import com.opsvision.harness.model.dto.InvestigationResponse;
import com.opsvision.harness.model.dto.SessionContext;
import com.opsvision.harness.model.dto.ToolResult;
import com.opsvision.harness.model.entity.Conversation;
import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.enums.SessionStatus;
import com.opsvision.harness.model.enums.ToolType;
import com.opsvision.harness.repository.ConversationRepository;
import com.opsvision.harness.service.ai.ChatService;
import com.opsvision.harness.service.context.ContextService;
import com.opsvision.harness.service.context.SessionService;
import com.opsvision.harness.service.mcp.McpClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class AgentService {
    
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    
    // Patterns to detect what type of investigation is needed
    private static final Pattern ORDER_PATTERN = Pattern.compile("order\\s+(\\w+)|order\\s+#?(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHIPPING_PATTERN = Pattern.compile("ship|shipping|shipment|delivery", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVENTORY_PATTERN = Pattern.compile("inventory|stock|item", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUDIT_PATTERN = Pattern.compile("audit|history|timeline|trace", Pattern.CASE_INSENSITIVE);
    
    @Autowired
    private SessionService sessionService;
    
    @Autowired
    private ContextService contextService;
    
    @Autowired
    private McpClientService mcpClientService;
    
    @Autowired
    private ChatService chatService;
    
    @Autowired
    private ConversationRepository conversationRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    public InvestigationResponse investigate(InvestigationRequest request, UUID sessionId) {
        log.info("Starting investigation for session: {} with query: {}", sessionId, request.getQuery());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Get or create session
            Session session = getOrCreateSession(request, sessionId);
            
            // Create conversation record
            Conversation conversation = createConversation(session, request.getQuery());
            
            // Select and execute tools
            List<ToolType> selectedTools = selectTools(request.getQuery());
            String orderId = extractOrderId(request.getQuery());
            
            log.info("Selected tools: {} for order: {}", selectedTools, orderId);
            
            // Execute MCP tools
            List<ToolResult> toolResults = mcpClientService.invokeTools(selectedTools, orderId);
            
            // Build context
            SessionContext context = contextService.buildContext(session.getId(), toolResults);
            
            // Store context and tool executions
            contextService.storeContext(session.getId(), context);
            contextService.persistToolExecutions(conversation.getId(), toolResults);
            
            // Generate AI-powered response
            String response = chatService.generateInvestigation(context, request.getQuery());
            
            // Update conversation with response
            conversation.setResponse(response);
            try {
                conversation.setContextData(objectMapper.valueToTree(context));
            } catch (Exception e) {
                log.warn("Failed to serialize context data: {}", e.getMessage());
            }
            conversationRepository.save(conversation);
            
            // Update session
            sessionService.updateSession(session.getId(), SessionStatus.COMPLETED);
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Investigation completed for session {} in {}ms", sessionId, executionTime);
            
            return new InvestigationResponse(
                session.getId(),
                response,
                toolResults,
                executionTime,
                true
            );
            
        } catch (Exception e) {
            log.error("Investigation failed for session {}: {}", sessionId, e.getMessage(), e);
            
            if (sessionId != null) {
                sessionService.updateSession(sessionId, SessionStatus.FAILED);
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            return new InvestigationResponse(
                sessionId,
                "Investigation failed: " + e.getMessage(),
                List.of(),
                executionTime,
                false
            );
        }
    }

    public InvestigationResponse continueInvestigation(UUID sessionId, String followUpQuery, String userId) {
        log.info("Continuing investigation for session: {} with query: {}", sessionId, followUpQuery);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Get existing session
            Session session = sessionService.getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
                
            if (!session.getUserId().equals(userId)) {
                throw new IllegalArgumentException("Session does not belong to user: " + userId);
            }
            
            // Create conversation record
            Conversation conversation = createConversation(session, followUpQuery);
            
            // Get session context
            SessionContext context = contextService.getSessionContext(sessionId);
            
            // Generate follow-up response using AI
            String response = chatService.generateFollowUpResponse(context, followUpQuery);
            
            // Update conversation with response
            conversation.setResponse(response);
            try {
                conversation.setContextData(objectMapper.valueToTree(context));
            } catch (Exception e) {
                log.warn("Failed to serialize context data: {}", e.getMessage());
            }
            conversationRepository.save(conversation);
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Follow-up response completed for session {} in {}ms", sessionId, executionTime);
            
            return new InvestigationResponse(
                sessionId,
                response,
                context.getToolResults(),
                executionTime,
                true
            );
            
        } catch (Exception e) {
            log.error("Follow-up investigation failed for session {}: {}", sessionId, e.getMessage(), e);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            return new InvestigationResponse(
                sessionId,
                "Follow-up investigation failed: " + e.getMessage(),
                false
            );
        }
    }

    public List<ToolType> selectTools(String query) {
        log.debug("Selecting tools for query: {}", query);
        
        // Simple rule-based tool selection
        // In a more sophisticated system, this could use ML or AI to select tools
        
        String lowerQuery = query.toLowerCase();
        List<ToolType> selectedTools = List.of();
        
        if (SHIPPING_PATTERN.matcher(query).find()) {
            // Shipping-related queries need order timeline and task history
            selectedTools = List.of(
                ToolType.GET_ORDER_TIMELINE,
                ToolType.GET_TASK_HISTORY,
                ToolType.GET_AUDIT_EVENTS
            );
        } else if (INVENTORY_PATTERN.matcher(query).find()) {
            // Inventory-related queries need inventory history
            selectedTools = List.of(
                ToolType.GET_INVENTORY_HISTORY,
                ToolType.GET_ORDER_TIMELINE,
                ToolType.GET_AUDIT_EVENTS
            );
        } else if (AUDIT_PATTERN.matcher(query).find()) {
            // Audit/timeline queries need all audit information
            selectedTools = List.of(
                ToolType.GET_AUDIT_EVENTS,
                ToolType.GET_ORDER_TIMELINE,
                ToolType.GET_TASK_HISTORY
            );
        } else {
            // Default: run comprehensive investigation
            selectedTools = List.of(
                ToolType.GET_ORDER_TIMELINE,
                ToolType.GET_TASK_HISTORY,
                ToolType.GET_INVENTORY_HISTORY,
                ToolType.GET_AUDIT_EVENTS
            );
        }
        
        log.info("Selected {} tools for query: {}", selectedTools.size(), selectedTools);
        return selectedTools;
    }

    public String buildFinalResponse(List<ToolResult> toolResults, String query) {
        log.debug("Building final response from {} tool results", toolResults.size());
        
        // Simple response builder - will be enhanced with Spring AI integration
        StringBuilder response = new StringBuilder();
        response.append("Investigation Results:\n\n");
        
        // Summary
        long successfulTools = toolResults.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        response.append(String.format("Executed %d tools (%d successful, %d failed)\n\n", 
                                    toolResults.size(), successfulTools, toolResults.size() - successfulTools));
        
        // Tool-specific results
        for (ToolResult result : toolResults) {
            response.append("Tool: ").append(result.getToolName()).append("\n");
            response.append("Status: ").append(result.getStatus()).append("\n");
            
            if (result.isSuccess()) {
                response.append("Execution Time: ").append(result.getExecutionTimeMs()).append("ms\n");
                
                if (result.getResult() != null) {
                    response.append("Data Retrieved: ").append(summarizeToolResult(result)).append("\n");
                }
            } else {
                response.append("Error: ").append(result.getErrorMessage()).append("\n");
            }
            response.append("\n");
        }
        
        // Simple analysis
        if (successfulTools > 0) {
            response.append("Analysis:\n");
            response.append("Based on the retrieved data, please review the tool outputs above for detailed information about the order status and any issues.\n");
        } else {
            response.append("Unable to retrieve data due to tool execution failures. Please try again or contact support.\n");
        }
        
        return response.toString();
    }

    private Session getOrCreateSession(InvestigationRequest request, UUID sessionId) {
        if (sessionId != null) {
            Optional<Session> existingSession = sessionService.getSession(sessionId);
            if (existingSession.isPresent()) {
                return existingSession.get();
            }
        }
        
        return sessionService.createSession(request.getUserId(), request.getQuery());
    }

    private Conversation createConversation(Session session, String query) {
        // Get next sequence number
        Optional<Integer> maxSequence = conversationRepository.findMaxSequenceNumberForSession(session.getId());
        int nextSequence = maxSequence.orElse(0) + 1;
        
        Conversation conversation = new Conversation(session, nextSequence, query);
        return conversationRepository.save(conversation);
    }

    private String extractOrderId(String query) {
        Matcher matcher = ORDER_PATTERN.matcher(query);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        }
        
        // Default order ID for testing - in real system this would be extracted more intelligently
        return "12345";
    }

    private String summarizeToolResult(ToolResult result) {
        if (result.getResult() == null) {
            return "No data";
        }
        
        try {
            if (result.getResult().isArray()) {
                return String.format("%d items retrieved", result.getResult().size());
            } else if (result.getResult().isObject()) {
                return String.format("Object with %d fields", result.getResult().size());
            } else {
                String text = result.getResult().asText();
                return text.length() > 100 ? text.substring(0, 100) + "..." : text;
            }
        } catch (Exception e) {
            return "Data retrieved (format unknown)";
        }
    }
}