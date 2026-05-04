package com.opsvision.harness.service.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.harness.model.dto.SessionContext;
import com.opsvision.harness.model.dto.ToolResult;
import com.opsvision.harness.model.entity.Conversation;
import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.entity.ToolExecution;
import com.opsvision.harness.model.enums.ToolExecutionStatus;
import com.opsvision.harness.repository.ConversationRepository;
import com.opsvision.harness.repository.ToolExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ContextService {
    
    private static final Logger log = LoggerFactory.getLogger(ContextService.class);
    private static final String CONVERSATION_CONTEXT_KEY = "conversation:%s:context";
    private static final String TOOL_CACHE_KEY = "tool:cache:%s:%s";
    private static final int MAX_CONVERSATION_HISTORY = 5;
    
    @Autowired
    private SessionService sessionService;
    
    @Autowired
    private ConversationRepository conversationRepository;
    
    @Autowired
    private ToolExecutionRepository toolExecutionRepository;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private Duration conversationTtl;

    public SessionContext buildContext(UUID sessionId, List<ToolResult> toolResults) {
        log.debug("Building context for session: {}", sessionId);
        
        Session session = sessionService.getSessionWithConversations(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
            
        SessionContext context = new SessionContext();
        context.setSessionId(sessionId);
        context.setUserId(session.getUserId());
        context.setInitialQuery(session.getInitialQuery());
        context.setStatus(session.getStatus());
        context.setCreatedAt(session.getCreatedAt());
        context.setUpdatedAt(session.getUpdatedAt());
        context.setMetadata(session.getMetadata());
        
        // Add conversation history
        List<SessionContext.ConversationSummary> conversationHistory = buildConversationHistory(sessionId);
        context.setConversationHistory(conversationHistory);
        
        // Add tool results
        context.setToolResults(toolResults);
        
        // Build aggregated context string
        String aggregatedContext = buildAggregatedContextString(context, toolResults);
        context.setAggregatedContext(aggregatedContext);
        
        log.debug("Built context with {} conversations and {} tool results", 
                 conversationHistory.size(), toolResults.size());
        return context;
    }

    public void storeContext(UUID sessionId, SessionContext context) {
        log.debug("Storing context for session: {}", sessionId);
        
        // Cache the context in Redis
        String contextKey = String.format(CONVERSATION_CONTEXT_KEY, sessionId);
        redisTemplate.opsForValue().set(contextKey, context, conversationTtl);
        
        // Cache successful tool results for potential reuse
        if (context.getToolResults() != null) {
            context.getToolResults().stream()
                .filter(ToolResult::isSuccess)
                .forEach(this::cacheToolResult);
        }
    }

    public SessionContext getSessionContext(UUID sessionId) {
        log.debug("Retrieving context for session: {}", sessionId);
        
        // Try cache first
        String contextKey = String.format(CONVERSATION_CONTEXT_KEY, sessionId);
        SessionContext cachedContext = (SessionContext) redisTemplate.opsForValue().get(contextKey);
        
        if (cachedContext != null) {
            log.debug("Retrieved context from cache for session: {}", sessionId);
            return cachedContext;
        }
        
        // Build fresh context if not cached
        log.debug("Building fresh context for session: {}", sessionId);
        return buildContext(sessionId, List.of());
    }

    public ToolResult getCachedToolResult(String toolName, Map<String, Object> parameters) {
        String paramHash = generateParameterHash(parameters);
        String cacheKey = String.format(TOOL_CACHE_KEY, toolName, paramHash);
        
        ToolResult cached = (ToolResult) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Found cached tool result for {} with hash: {}", toolName, paramHash);
        }
        
        return cached;
    }

    public List<ToolResult> getCachedResults(List<String> toolNames) {
        return toolNames.stream()
            .map(toolName -> getCachedToolResult(toolName, Map.of()))
            .filter(result -> result != null)
            .collect(Collectors.toList());
    }

    private List<SessionContext.ConversationSummary> buildConversationHistory(UUID sessionId) {
        List<Conversation> conversations = conversationRepository.findLastNConversationsForSession(
            sessionId, MAX_CONVERSATION_HISTORY);
            
        return conversations.stream()
            .map(this::mapToConversationSummary)
            .collect(Collectors.toList());
    }

    private SessionContext.ConversationSummary mapToConversationSummary(Conversation conversation) {
        return new SessionContext.ConversationSummary(
            conversation.getId(),
            conversation.getSequenceNumber(),
            conversation.getQuery(),
            conversation.getResponse(),
            conversation.getCreatedAt()
        );
    }

    private String buildAggregatedContextString(SessionContext context, List<ToolResult> toolResults) {
        StringBuilder contextBuilder = new StringBuilder();
        
        // Add initial query
        contextBuilder.append("Initial Query: ").append(context.getInitialQuery()).append("\n\n");
        
        // Add conversation history
        if (context.getConversationHistory() != null && !context.getConversationHistory().isEmpty()) {
            contextBuilder.append("Previous Conversations:\n");
            context.getConversationHistory().forEach(conv -> {
                contextBuilder.append("Q: ").append(conv.getQuery()).append("\n");
                if (conv.getResponse() != null) {
                    contextBuilder.append("A: ").append(conv.getResponse()).append("\n");
                }
                contextBuilder.append("\n");
            });
        }
        
        // Add tool results
        if (toolResults != null && !toolResults.isEmpty()) {
            contextBuilder.append("Tool Results:\n");
            toolResults.forEach(result -> {
                contextBuilder.append("Tool: ").append(result.getToolName()).append("\n");
                contextBuilder.append("Status: ").append(result.getStatus()).append("\n");
                
                if (result.isSuccess() && result.getResult() != null) {
                    contextBuilder.append("Result: ").append(formatJsonForContext(result.getResult())).append("\n");
                } else if (result.getErrorMessage() != null) {
                    contextBuilder.append("Error: ").append(result.getErrorMessage()).append("\n");
                }
                contextBuilder.append("\n");
            });
        }
        
        return contextBuilder.toString();
    }

    private String formatJsonForContext(JsonNode jsonNode) {
        try {
            if (jsonNode.isTextual()) {
                return jsonNode.asText();
            } else if (jsonNode.isObject() || jsonNode.isArray()) {
                return objectMapper.writeValueAsString(jsonNode);
            } else {
                return jsonNode.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to format JSON for context: {}", e.getMessage());
            return jsonNode.toString();
        }
    }

    private void cacheToolResult(ToolResult toolResult) {
        if (toolResult.getParameters() != null) {
            String paramHash = generateParameterHash(toolResult.getParameters());
            String cacheKey = String.format(TOOL_CACHE_KEY, toolResult.getToolName(), paramHash);
            
            Duration toolTtl = Duration.ofMinutes(30);
            redisTemplate.opsForValue().set(cacheKey, toolResult, toolTtl);
            
            log.debug("Cached tool result for {} with hash: {}", toolResult.getToolName(), paramHash);
        }
    }

    private String generateParameterHash(Map<String, Object> parameters) {
        try {
            String paramJson = objectMapper.writeValueAsString(parameters);
            return String.valueOf(paramJson.hashCode());
        } catch (Exception e) {
            log.warn("Failed to generate parameter hash: {}", e.getMessage());
            return String.valueOf(parameters.hashCode());
        }
    }

    public void persistToolExecutions(UUID conversationId, List<ToolResult> toolResults) {
        log.debug("Persisting {} tool executions for conversation: {}", toolResults.size(), conversationId);
        
        List<ToolExecution> executions = toolResults.stream()
            .map(result -> mapToToolExecution(conversationId, result))
            .collect(Collectors.toList());
            
        toolExecutionRepository.saveAll(executions);
        
        log.info("Persisted {} tool executions", executions.size());
    }

    private ToolExecution mapToToolExecution(UUID conversationId, ToolResult toolResult) {
        ToolExecution execution = new ToolExecution();
        execution.setConversation(conversationRepository.getReferenceById(conversationId));
        execution.setToolName(toolResult.getToolName());
        execution.setStatus(mapStatus(toolResult.getStatus()));
        execution.setExecutionTimeMs(toolResult.getExecutionTimeMs());
        execution.setErrorMessage(toolResult.getErrorMessage());
        
        try {
            if (toolResult.getParameters() != null) {
                execution.setParameters(objectMapper.valueToTree(toolResult.getParameters()));
            }
            if (toolResult.getResult() != null) {
                execution.setResult(toolResult.getResult());
            }
        } catch (Exception e) {
            log.warn("Failed to serialize tool execution data: {}", e.getMessage());
        }
        
        return execution;
    }

    private com.opsvision.harness.model.enums.ToolExecutionStatus mapStatus(
            com.opsvision.harness.model.enums.ToolExecutionStatus status) {
        return status; // Direct mapping since they're the same enum
    }
}