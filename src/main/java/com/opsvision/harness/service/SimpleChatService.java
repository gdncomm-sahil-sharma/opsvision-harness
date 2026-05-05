package com.opsvision.harness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.harness.model.dto.ChatResponse;
import com.opsvision.harness.model.entity.Conversation;
import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.entity.ToolExecution;
import com.opsvision.harness.model.enums.ToolExecutionStatus;
import com.opsvision.harness.repository.ConversationRepository;
import com.opsvision.harness.repository.ToolExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Drives the chat endpoint using Spring AI's tool-calling loop, with
 * multi-turn memory keyed by Session and a persisted audit trail in
 * Conversation + ToolExecution rows.
 *
 * Per request:
 * 1. Resolve the user's active Session (or create one) via {@link SessionService}.
 * 2. Persist a Conversation row for this turn (response filled in at end).
 * 3. Build a per-request ChatClient with:
 *    - Spring AI's MessageChatMemoryAdvisor keyed by sessionId, so prior
 *      turns in the session are prepended to the prompt automatically.
 *    - Recording tool callbacks that persist a ToolExecution row per
 *      MCP tool invocation and capture the tool name into a ThreadLocal.
 * 4. Invoke the tool-calling loop; let Spring AI handle LLM ↔ tool round trips.
 * 5. Update the Conversation row with the assistant's final content.
 *
 * The autoconfigured ChatMemory bean is an in-process MessageWindowChatMemory.
 * Memory does not survive app restarts; persistence of message history to
 * Postgres can be layered on later via a custom ChatMemoryRepository.
 */
@Service
public class SimpleChatService {

    private static final Logger log = LoggerFactory.getLogger(SimpleChatService.class);

    private static final ThreadLocal<List<String>> INVOKED_TOOLS =
            ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<UUID> CURRENT_CONVERSATION_ID = new ThreadLocal<>();

    @Autowired
    private ChatModel chatModel;

    @Autowired(required = false)
    private SyncMcpToolCallbackProvider mcpToolProvider;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ToolExecutionRepository toolExecutionRepository;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    @Qualifier("toolResultsTtl")
    private Duration toolResultsTtl;

    private static final String TOOL_CACHE_PREFIX = "tool-result:";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String llmPrompt;

    private String getLLMPrompt() {
        if (llmPrompt == null) {
            try {
                ClassPathResource resource = new ClassPathResource("llm-prompt.txt");
                llmPrompt = resource.getContentAsString(StandardCharsets.UTF_8);
                log.info("LLM system prompt loaded ({} chars)", llmPrompt.length());
            } catch (IOException e) {
                log.error("Failed to load llm-prompt.txt", e);
                llmPrompt = "You are a helpful warehouse operations assistant.";
            }
        }
        return llmPrompt;
    }

    public ChatResponse processMessage(String userId, String message) {
        log.info("Processing message for user '{}': {}", userId, message);
        INVOKED_TOOLS.get().clear();
        Conversation conversation = null;

        try {
            Session session = sessionService.getOrCreateActiveSession(userId, message);
            conversation = persistInitialConversation(session, message);
            CURRENT_CONVERSATION_ID.set(conversation.getId());

            ToolCallback[] callbacks = wrappedCallbacks();
            log.info("ChatClient prompt: session={} conversation={} tools={}",
                    session.getId(), conversation.getId(), callbacks.length);

            MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                    .conversationId(session.getId().toString())
                    .build();

            ChatClient client = ChatClient.builder(chatModel)
                    .defaultSystem(getLLMPrompt())
                    .defaultAdvisors(memoryAdvisor)
                    .build();

            String content = client.prompt()
                    .user(message)
                    .toolCallbacks(callbacks)
                    .call()
                    .content();

            conversation.setResponse(content);
            conversationRepository.save(conversation);

            List<String> toolsUsed = new ArrayList<>(INVOKED_TOOLS.get());
            log.info("Chat completed; conversation={} tools={}", conversation.getId(), toolsUsed);

            ChatResponse response = new ChatResponse();
            response.setResponse(content);
            response.setSuccess(true);
            response.setToolsUsed(toolsUsed);
            return response;

        } catch (Exception e) {
            log.error("Error processing chat message", e);
            if (conversation != null) {
                try {
                    conversation.setResponse("ERROR: " + e.getMessage());
                    conversationRepository.save(conversation);
                } catch (Exception persistError) {
                    log.warn("Failed to persist error response: {}", persistError.getMessage());
                }
            }
            ChatResponse error = new ChatResponse();
            error.setSuccess(false);
            error.setError(e.getMessage());
            error.setResponse("I encountered an error processing your request. Please try again.");
            error.setToolsUsed(new ArrayList<>(INVOKED_TOOLS.get()));
            return error;
        } finally {
            INVOKED_TOOLS.remove();
            CURRENT_CONVERSATION_ID.remove();
        }
    }

    private Conversation persistInitialConversation(Session session, String message) {
        int seq = conversationRepository.findMaxSequenceNumberForSession(session.getId())
                .orElse(0) + 1;
        Conversation conv = new Conversation(session, seq, message);
        return conversationRepository.save(conv);
    }

    private ToolCallback[] wrappedCallbacks() {
        if (mcpToolProvider == null) {
            return new ToolCallback[0];
        }
        ToolCallback[] raw = mcpToolProvider.getToolCallbacks();
        if (raw == null || raw.length == 0) {
            return new ToolCallback[0];
        }
        ToolCallback[] wrapped = new ToolCallback[raw.length];
        for (int i = 0; i < raw.length; i++) {
            wrapped[i] = new RecordingToolCallback(raw[i]);
        }
        return wrapped;
    }

    private JsonNode parseJsonOrNull(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(json);
        }
    }

    private String cacheKey(String toolName, String args) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(args == null ? new byte[0] : args.getBytes(StandardCharsets.UTF_8));
            return TOOL_CACHE_PREFIX + toolName + ":" + HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            return TOOL_CACHE_PREFIX + toolName + ":" + (args == null ? 0 : args.hashCode());
        }
    }

    private String lookupCache(String key) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.debug("Cache lookup failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void storeCache(String key, String value) {
        if (redisTemplate == null || value == null || value.isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, toolResultsTtl);
        } catch (Exception e) {
            log.debug("Cache store failed for key {}: {}", key, e.getMessage());
        }
    }

    private final class RecordingToolCallback implements ToolCallback {

        private final ToolCallback delegate;

        RecordingToolCallback(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String toolInput) {
            return record(toolInput, () -> delegate.call(toolInput));
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return record(toolInput, () -> delegate.call(toolInput, toolContext));
        }

        private String record(String toolInput, Supplier<String> invoke) {
            String name = safeName();
            INVOKED_TOOLS.get().add(name);
            UUID convId = CURRENT_CONVERSATION_ID.get();

            String cacheKey = cacheKey(name, toolInput);
            String cached = lookupCache(cacheKey);
            if (cached != null) {
                log.info("MCP tool cache HIT: {} key={}", name, cacheKey);
                persistToolExecution(convId, name, toolInput, cached, 0,
                        ToolExecutionStatus.SUCCESS, "cache-hit");
                return cached;
            }

            long start = System.currentTimeMillis();
            try {
                String result = invoke.get();
                long elapsed = System.currentTimeMillis() - start;
                log.info("MCP tool invoked: {} latency_ms={} input={}",
                        name, elapsed, toolInput);
                storeCache(cacheKey, result);
                persistToolExecution(convId, name, toolInput, result, elapsed,
                        ToolExecutionStatus.SUCCESS, null);
                return result;
            } catch (RuntimeException e) {
                long elapsed = System.currentTimeMillis() - start;
                log.warn("MCP tool failed: {} latency_ms={} error={}",
                        name, elapsed, e.getMessage());
                persistToolExecution(convId, name, toolInput, null, elapsed,
                        ToolExecutionStatus.FAILED, e.getMessage());
                throw e;
            }
        }

        private void persistToolExecution(UUID convId, String name, String input,
                                          String result, long elapsedMs,
                                          ToolExecutionStatus status, String error) {
            if (convId == null) {
                return;
            }
            try {
                Conversation convRef = conversationRepository.getReferenceById(convId);
                ToolExecution te = new ToolExecution();
                te.setConversation(convRef);
                te.setToolName(name);
                te.setParameters(parseJsonOrNull(input));
                te.setResult(parseJsonOrNull(result));
                te.setExecutionTimeMs((int) elapsedMs);
                te.setStatus(status);
                te.setErrorMessage(error);
                toolExecutionRepository.save(te);
            } catch (Exception e) {
                log.warn("Failed to persist ToolExecution for {}: {}", name, e.getMessage());
            }
        }

        private String safeName() {
            try {
                ToolDefinition def = delegate.getToolDefinition();
                return def != null ? def.name() : "unknown";
            } catch (Exception e) {
                return "unknown";
            }
        }
    }
}
