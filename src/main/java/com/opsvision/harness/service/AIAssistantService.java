package com.opsvision.harness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opsvision.harness.model.dto.response.ChatResponseData;
import com.opsvision.harness.model.dto.response.StreamEvent;
import com.opsvision.harness.model.dto.response.TextResponse;
import com.opsvision.harness.model.entity.Conversation;
import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.entity.ToolExecution;
import com.opsvision.harness.model.enums.ToolExecutionStatus;
import com.opsvision.harness.repository.ConversationRepository;
import com.opsvision.harness.repository.ToolExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Single chat service for /api/chat. Drives Spring AI's tool-calling loop
 * against MCP tools, attaches per-session memory, persists a Conversation
 * row plus a ToolExecution row per tool call, caches tool results in Redis,
 * and shapes the final assistant message into ChatResponseData via
 * {@code chatClient.prompt(...).call().entity(ChatResponseData.class)} —
 * which lets the LLM call tools as needed and emit a JSON object matching
 * the response schema as its final message.
 */
@Service
public class AIAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AIAssistantService.class);

    private static final String TOOL_CACHE_PREFIX = "tool-result:";

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String llmPrompt;

    public ChatResponseData generateStructuredResponse(String userId, String userMessage) {
        MDC.put("userId", userId);
        log.info("Processing structured chat for user '{}': {}", userId, userMessage);
        List<String> invokedTools = Collections.synchronizedList(new ArrayList<>());
        Conversation conversation = null;

        try {
            Session session = sessionService.getOrCreateActiveSession(userId, userMessage);
            conversation = persistInitialConversation(session, userMessage);
            MDC.put("conversationId", conversation.getId().toString());

            ToolCallback[] callbacks = wrappedCallbacks(null, conversation.getId(), invokedTools, userId);
            log.info("ChatClient prompt: session={} conversation={} tools={}",
                    session.getId(), conversation.getId(), callbacks.length);

            MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                    .conversationId(session.getId().toString())
                    .build();

            ChatClient client = ChatClient.builder(chatModel)
                    .defaultSystem(getLLMPrompt())
                    .defaultAdvisors(memoryAdvisor)
                    .build();

            ChatResponseData responseData = client.prompt()
                    .user(userMessage)
                    .toolCallbacks(callbacks)
                    .call()
                    .entity(ChatResponseData.class);

            String summary = (responseData != null && responseData.getTextResponse() != null)
                    ? responseData.getTextResponse().getSummary()
                    : "(no summary)";
            conversation.setResponse(summary);
            conversation.setContextData(referencesAsJson(responseData));
            conversationRepository.save(conversation);

            log.info("Chat completed; conversation={} tools={}",
                    conversation.getId(), invokedTools);
            return responseData;

        } catch (Exception e) {
            log.error("Error processing structured chat", e);
            if (conversation != null) {
                try {
                    conversation.setResponse("ERROR: " + e.getMessage());
                    conversationRepository.save(conversation);
                } catch (Exception persistError) {
                    log.warn("Failed to persist error response: {}", persistError.getMessage());
                }
            }
            return createFallbackResponse(userMessage, e);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Streaming variant of {@link #generateStructuredResponse}. Emits SSE events
     * as the LLM works: a {@code tool_call_start} / {@code tool_call_end} pair
     * around each MCP tool invocation, an {@code assistant_token} per token chunk
     * from the model, and a final {@code final} event with the parsed
     * {@link ChatResponseData}. Errors surface as a single {@code error} event.
     *
     * <p>The full chat pipeline (memory advisor, RecordingToolCallback wrapping,
     * cache lookups, ToolExecution persistence, Conversation persistence) runs on
     * a {@link Schedulers#boundedElastic()} worker so the controller's reactive
     * thread is not blocked while tools execute and the model streams.
     */
    public Flux<StreamEvent> streamStructuredResponse(String userId, String userMessage) {
        log.info("Streaming structured chat for user '{}': {}", userId, userMessage);
        Sinks.Many<StreamEvent> sink = Sinks.many().unicast().onBackpressureBuffer();

        Mono.fromRunnable(() -> runStreamInternal(userId, userMessage, sink))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        return sink.asFlux();
    }

    private void runStreamInternal(String userId, String userMessage, Sinks.Many<StreamEvent> sink) {
        MDC.put("userId", userId);
        List<String> invokedTools = Collections.synchronizedList(new ArrayList<>());
        Conversation conversation = null;

        try {
            Session session = sessionService.getOrCreateActiveSession(userId, userMessage);
            conversation = persistInitialConversation(session, userMessage);
            MDC.put("conversationId", conversation.getId().toString());

            ToolCallback[] callbacks = wrappedCallbacks(sink, conversation.getId(), invokedTools, userId);
            log.info("Stream prompt: session={} conversation={} tools={}",
                    session.getId(), conversation.getId(), callbacks.length);

            MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                    .conversationId(session.getId().toString())
                    .build();

            ChatClient client = ChatClient.builder(chatModel)
                    .defaultSystem(getLLMPrompt())
                    .defaultAdvisors(memoryAdvisor)
                    .build();

            BeanOutputConverter<ChatResponseData> converter =
                    new BeanOutputConverter<>(ChatResponseData.class);

            StringBuilder accumulated = new StringBuilder();
            JsonComponentSplitter splitter = new JsonComponentSplitter(objectMapper);
            final Conversation conv = conversation;
            final String capturedUserId = userId;
            final String capturedConvId = conversation.getId().toString();

            client.prompt()
                    .user(userMessage + "\n\n" + converter.getFormat())
                    .toolCallbacks(callbacks)
                    .stream()
                    .content()
                    .doOnNext(token -> {
                        accumulated.append(token);
                        sink.tryEmitNext(StreamEvent.assistantToken(token));
                        for (StreamEvent componentEvent : splitter.consume(token)) {
                            sink.tryEmitNext(componentEvent);
                        }
                    })
                    .doOnError(err -> withMdc(capturedUserId, capturedConvId, () -> {
                        log.error("Stream error", err);
                        sink.tryEmitNext(StreamEvent.error(err.getMessage()));
                        try {
                            conv.setResponse("ERROR: " + err.getMessage());
                            conversationRepository.save(conv);
                        } catch (Exception ignore) {
                            // best effort
                        }
                    }))
                    .doOnComplete(() -> withMdc(capturedUserId, capturedConvId, () -> {
                        String fullText = accumulated.toString();
                        ChatResponseData data = null;
                        try {
                            data = converter.convert(fullText);
                        } catch (Exception parseError) {
                            log.warn("Stream final parse failed: {}", parseError.getMessage());
                            sink.tryEmitNext(StreamEvent.error(
                                    "structured-output parse failed: " + parseError.getMessage()));
                        }
                        if (data != null) {
                            String summary = data.getTextResponse() != null
                                    ? data.getTextResponse().getSummary()
                                    : "(no summary)";
                            try {
                                conv.setResponse(summary);
                                conv.setContextData(referencesAsJson(data));
                                conversationRepository.save(conv);
                            } catch (Exception persistError) {
                                log.warn("Failed to persist streaming conversation: {}",
                                        persistError.getMessage());
                            }
                            sink.tryEmitNext(StreamEvent.finalResponse(data));
                        }
                        log.info("Stream completed; conversation={} tools={}",
                                conv.getId(), invokedTools);
                    }))
                    .doFinally(sig -> sink.tryEmitComplete())
                    .blockLast();

        } catch (Exception e) {
            log.error("Stream setup failed", e);
            sink.tryEmitNext(StreamEvent.error(e.getMessage()));
            if (conversation != null) {
                try {
                    conversation.setResponse("ERROR: " + e.getMessage());
                    conversationRepository.save(conversation);
                } catch (Exception ignore) {
                    // best effort
                }
            }
            sink.tryEmitComplete();
        } finally {
            MDC.clear();
        }
    }

    /**
     * Run a small block of code with MDC populated. Used inside Reactor doOn*
     * callbacks where the thread may differ from the worker that initially set
     * MDC, so log lines emitted from the callback would otherwise come back
     * empty for [user=…] [conv=…].
     */
    private void withMdc(String userId, String conversationId, Runnable body) {
        String prevUser = MDC.get("userId");
        String prevConv = MDC.get("conversationId");
        if (userId != null) MDC.put("userId", userId);
        if (conversationId != null) MDC.put("conversationId", conversationId);
        try {
            body.run();
        } finally {
            restoreMdc("userId", prevUser);
            restoreMdc("conversationId", prevConv);
        }
    }

    private void restoreMdc(String key, String prev) {
        if (prev == null) MDC.remove(key);
        else MDC.put(key, prev);
    }

    private ChatResponseData createFallbackResponse(String userMessage, Exception error) {
        TextResponse text = new TextResponse(
                "I encountered an error processing your request: " + error.getMessage(),
                List.of(
                        "The error has been logged for investigation.",
                        "Please try again or rephrase the question."
                )
        );
        return new ChatResponseData(text, null, null);
    }

    private Conversation persistInitialConversation(Session session, String message) {
        int seq = conversationRepository.findMaxSequenceNumberForSession(session.getId())
                .orElse(0) + 1;
        Conversation conv = new Conversation(session, seq, message);
        return conversationRepository.save(conv);
    }

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

    private ToolCallback[] wrappedCallbacks(Sinks.Many<StreamEvent> stream,
                                            UUID conversationId,
                                            List<String> invokedTools,
                                            String userId) {
        if (mcpToolProvider == null) {
            return new ToolCallback[0];
        }
        ToolCallback[] raw = mcpToolProvider.getToolCallbacks();
        if (raw == null || raw.length == 0) {
            return new ToolCallback[0];
        }
        ToolCallback[] wrapped = new ToolCallback[raw.length];
        for (int i = 0; i < raw.length; i++) {
            wrapped[i] = new RecordingToolCallback(raw[i], stream, conversationId, invokedTools, userId);
        }
        return wrapped;
    }

    private JsonNode referencesAsJson(ChatResponseData data) {
        if (data == null || data.getReferences() == null || data.getReferences().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.valueToTree(data.getReferences());
        } catch (Exception e) {
            log.debug("Failed to serialize references for context_data: {}", e.getMessage());
            return null;
        }
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
        if (looksLikeEntityNotFound(value)) {
            log.debug("Skipping cache for not-found result key={}", key);
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, toolResultsTtl);
        } catch (Exception e) {
            log.debug("Cache store failed for key {}: {}", key, e.getMessage());
        }
    }

    /**
     * Detect MCP responses that represent "entity not found" — typically a top-level
     * object whose substantive fields are all null or empty. Caching those poisons
     * follow-ups when the original tool call was wrong (e.g. PP code fed to
     * getPickList): a retry hits the cached null and reports "not found" again.
     *
     * MCP wraps tool output as [{"text": "<inner-json>"}], so unwrap that envelope
     * before checking.
     */
    private boolean looksLikeEntityNotFound(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            JsonNode payload = unwrapMcpEnvelope(root);
            if (payload == null || !payload.isObject() || payload.isEmpty()) {
                return false;
            }
            int substantive = 0;
            var iter = payload.fields();
            while (iter.hasNext()) {
                JsonNode field = iter.next().getValue();
                if (field == null || field.isNull()) continue;
                if (field.isArray() && field.isEmpty()) continue;
                if (field.isObject() && field.isEmpty()) continue;
                if (field.isTextual() && field.asText().isEmpty()) continue;
                substantive++;
            }
            return substantive == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Loop-1 self-correction nudge: when an MCP tool returns an "entity not found"
     * payload, append a generic routing hint to the result before handing it back
     * to the LLM. This lets the model retry within the same tool-calling loop
     * (e.g. realize {@code getPickList(223506)} on a PP-shaped input was wrong and
     * try {@code getPickPackage} instead) rather than reporting "not found" to the
     * user. Generic across every tool — no per-tool wiring.
     *
     * The hint is added as an extra MCP {@code text} content item alongside the
     * raw result, so the original tool output is still readable and the audit
     * trail stored in {@code tool_execution.result} captures the unmodified MCP
     * response (we persist before augmenting).
     */
    private String augmentNotFoundResult(String value) {
        String hint = "[ROUTING HINT: this tool returned no matching data. If the user's input contained " +
                "an identifier whose format could match multiple tool inputs (e.g. a code like " +
                "PK/MAR-01/V-2026/123456 vs a numeric pick_list.id, or a SKU vs a barcode), " +
                "double-check that the tool you called accepts that exact input shape. " +
                "Consider whether a sibling tool matches the input format better, and try once with that " +
                "tool before reporting 'not found' to the user.]";
        try {
            JsonNode root = objectMapper.readTree(value);
            if (root.isArray()) {
                ArrayNode arr = (ArrayNode) root;
                ObjectNode hintNode = objectMapper.createObjectNode();
                hintNode.put("type", "text");
                hintNode.put("text", hint);
                arr.add(hintNode);
                return objectMapper.writeValueAsString(arr);
            }
        } catch (Exception e) {
            log.debug("Failed to augment not-found result: {}", e.getMessage());
        }
        return value + "\n\n" + hint;
    }

    private JsonNode unwrapMcpEnvelope(JsonNode root) {
        if (root.isArray() && !root.isEmpty()) {
            JsonNode first = root.get(0);
            if (first.isObject() && first.has("text") && first.get("text").isTextual()) {
                try {
                    return objectMapper.readTree(first.get("text").asText());
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return root;
    }

    /**
     * Wraps a single MCP {@link ToolCallback} for one chat turn. Per-request
     * context (conversation id, invoked-tools accumulator, user id, optional
     * SSE sink) is injected at construction time rather than read from
     * ThreadLocals — Reactor schedulers don't propagate ThreadLocal across
     * thread switches, which previously caused streaming-path tool calls to
     * silently skip persistence (convId came back null) and lose log MDC.
     */
    private final class RecordingToolCallback implements ToolCallback {

        private final ToolCallback delegate;
        private final Sinks.Many<StreamEvent> stream;
        private final UUID conversationId;
        private final List<String> invokedTools;
        private final String userId;

        RecordingToolCallback(ToolCallback delegate,
                              Sinks.Many<StreamEvent> stream,
                              UUID conversationId,
                              List<String> invokedTools,
                              String userId) {
            this.delegate = delegate;
            this.stream = stream;
            this.conversationId = conversationId;
            this.invokedTools = invokedTools;
            this.userId = userId;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String toolInput) {
            return withMdcReturning(() -> record(toolInput, () -> delegate.call(toolInput)));
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return withMdcReturning(() -> record(toolInput, () -> delegate.call(toolInput, toolContext)));
        }

        private String withMdcReturning(Supplier<String> body) {
            String prevUser = MDC.get("userId");
            String prevConv = MDC.get("conversationId");
            if (userId != null) MDC.put("userId", userId);
            if (conversationId != null) MDC.put("conversationId", conversationId.toString());
            try {
                return body.get();
            } finally {
                restoreMdc("userId", prevUser);
                restoreMdc("conversationId", prevConv);
            }
        }

        private String record(String toolInput, Supplier<String> invoke) {
            String name = safeName();
            invokedTools.add(name);
            emit(StreamEvent.toolCallStart(name, toolInput));

            String cacheKey = cacheKey(name, toolInput);
            String cached = lookupCache(cacheKey);
            if (cached != null) {
                log.info("MCP tool cache HIT: {} key={}", name, cacheKey);
                persistToolExecution(conversationId, name, toolInput, cached, 0,
                        ToolExecutionStatus.SUCCESS, "cache-hit");
                emit(StreamEvent.toolCallEnd(name, 0, "CACHE_HIT", null));
                return cached;
            }

            long start = System.currentTimeMillis();
            try {
                String result = invoke.get();
                long elapsed = System.currentTimeMillis() - start;
                log.info("MCP tool invoked: {} latency_ms={} input={}",
                        name, elapsed, toolInput);
                storeCache(cacheKey, result);
                persistToolExecution(conversationId, name, toolInput, result, elapsed,
                        ToolExecutionStatus.SUCCESS, null);
                emit(StreamEvent.toolCallEnd(name, elapsed, "SUCCESS", null));
                if (looksLikeEntityNotFound(result)) {
                    log.info("MCP tool returned empty; appending routing-hint for self-correction: {}", name);
                    return augmentNotFoundResult(result);
                }
                return result;
            } catch (RuntimeException e) {
                long elapsed = System.currentTimeMillis() - start;
                log.warn("MCP tool failed: {} latency_ms={} error={}",
                        name, elapsed, e.getMessage());
                persistToolExecution(conversationId, name, toolInput, null, elapsed,
                        ToolExecutionStatus.FAILED, e.getMessage());
                emit(StreamEvent.toolCallEnd(name, elapsed, "FAILED", e.getMessage()));
                throw e;
            }
        }

        private void emit(StreamEvent event) {
            if (stream != null) {
                stream.tryEmitNext(event);
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
