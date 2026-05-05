package com.opsvision.harness.service;

import com.opsvision.harness.model.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives the chat endpoint using Spring AI's native tool-calling loop.
 *
 * Flow: build a ChatClient with the MCP tool callbacks attached, send the
 * user's message, let Spring AI orchestrate any LLM ↔ tool round trips, and
 * return the final assistant message. Tool names invoked during the call
 * are captured via a per-request thread-local so the response can list them.
 */
@Service
public class SimpleChatService {

    private static final Logger log = LoggerFactory.getLogger(SimpleChatService.class);

    private static final ThreadLocal<List<String>> INVOKED_TOOLS =
            ThreadLocal.withInitial(ArrayList::new);

    @Autowired
    private ChatModel chatModel;

    @Autowired(required = false)
    private SyncMcpToolCallbackProvider mcpToolProvider;

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

        try {
            ToolCallback[] callbacks = wrappedCallbacks();
            log.info("ChatClient prompt with {} MCP tool(s) attached", callbacks.length);

            ChatClient client = ChatClient.builder(chatModel)
                    .defaultSystem(getLLMPrompt())
                    .build();

            String content = client.prompt()
                    .user(message)
                    .toolCallbacks(callbacks)
                    .call()
                    .content();

            List<String> toolsUsed = new ArrayList<>(INVOKED_TOOLS.get());
            log.info("Chat completed; tools invoked: {}", toolsUsed);

            ChatResponse response = new ChatResponse();
            response.setResponse(content);
            response.setSuccess(true);
            response.setToolsUsed(toolsUsed);
            return response;

        } catch (Exception e) {
            log.error("Error processing chat message", e);
            ChatResponse error = new ChatResponse();
            error.setSuccess(false);
            error.setError(e.getMessage());
            error.setResponse("I encountered an error processing your request. Please try again.");
            error.setToolsUsed(new ArrayList<>(INVOKED_TOOLS.get()));
            return error;
        } finally {
            INVOKED_TOOLS.remove();
        }
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

    private static final class RecordingToolCallback implements ToolCallback {

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
            String name = safeName();
            INVOKED_TOOLS.get().add(name);
            log.info("MCP tool invoked: {} input={}", name, toolInput);
            return delegate.call(toolInput);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            String name = safeName();
            INVOKED_TOOLS.get().add(name);
            log.info("MCP tool invoked: {} input={} (with context)", name, toolInput);
            return delegate.call(toolInput, toolContext);
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
