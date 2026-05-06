package com.opsvision.harness.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.UUID;

/**
 * One event in the SSE stream from POST /api/chat/stream. Discriminator is
 * {@code type}; only the fields relevant to that type are populated. Sent
 * over SSE as JSON in the {@code data:} field of each event.
 *
 * Types:
 *   chat_id                 — chatId (resolved chat UUID; emitted FIRST so the UI can update its store immediately)
 *   tool_call_start         — toolName, input
 *   tool_call_end           — toolName, latencyMs, status (SUCCESS|FAILED|CACHE_HIT), errorMessage
 *   assistant_token         — token (incremental token chunk from the LLM)
 *   text_response_complete  — textResponse (full TextResponse object, parsed when its JSON closes)
 *   timeline_complete       — timeline (full Timeline object)
 *   table_complete          — table (full Table object)
 *   references_complete     — references (Map of identifiers from the prior turn)
 *   final                   — data (full ChatResponseData parsed from the accumulated stream)
 *   error                   — errorMessage
 *
 * The {@code *_complete} events fire as each top-level component object closes
 * during streaming, so a UI can render textResponse → timelines → table → references
 * incrementally without doing JSON-token assembly itself. The {@link ChatResponseData}
 * field order is pinned via {@code @JsonPropertyOrder} so the LLM emits — and
 * therefore this stream surfaces — components in that order.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamEvent {

    private final String type;
    private final String toolName;
    private final String input;
    private final Long latencyMs;
    private final String status;
    private final String token;
    private final ChatResponseData data;
    private final String errorMessage;
    private final TextResponse textResponse;
    private final Timeline timeline;
    private final Table table;
    private final Map<String, Object> references;
    private final UUID chatId;

    private StreamEvent(String type, String toolName, String input, Long latencyMs,
                        String status, String token, ChatResponseData data, String errorMessage,
                        TextResponse textResponse, Timeline timeline, Table table,
                        Map<String, Object> references, UUID chatId) {
        this.type = type;
        this.toolName = toolName;
        this.input = input;
        this.latencyMs = latencyMs;
        this.status = status;
        this.token = token;
        this.data = data;
        this.errorMessage = errorMessage;
        this.textResponse = textResponse;
        this.timeline = timeline;
        this.table = table;
        this.references = references;
        this.chatId = chatId;
    }

    public static StreamEvent chatId(UUID chatId) {
        return new StreamEvent("chat_id", null, null, null, null, null, null, null,
                null, null, null, null, chatId);
    }

    public static StreamEvent toolCallStart(String toolName, String input) {
        return new StreamEvent("tool_call_start", toolName, input, null, null, null, null, null,
                null, null, null, null, null);
    }

    public static StreamEvent toolCallEnd(String toolName, long latencyMs, String status, String errorMessage) {
        return new StreamEvent("tool_call_end", toolName, null, latencyMs, status, null, null, errorMessage,
                null, null, null, null, null);
    }

    public static StreamEvent assistantToken(String token) {
        return new StreamEvent("assistant_token", null, null, null, null, token, null, null,
                null, null, null, null, null);
    }

    public static StreamEvent textResponseComplete(TextResponse textResponse) {
        return new StreamEvent("text_response_complete", null, null, null, null, null, null, null,
                textResponse, null, null, null, null);
    }

    public static StreamEvent timelineComplete(Timeline timeline) {
        return new StreamEvent("timeline_complete", null, null, null, null, null, null, null,
                null, timeline, null, null, null);
    }

    public static StreamEvent tableComplete(Table table) {
        return new StreamEvent("table_complete", null, null, null, null, null, null, null,
                null, null, table, null, null);
    }

    public static StreamEvent referencesComplete(Map<String, Object> references) {
        return new StreamEvent("references_complete", null, null, null, null, null, null, null,
                null, null, null, references, null);
    }

    public static StreamEvent finalResponse(ChatResponseData data) {
        return new StreamEvent("final", null, null, null, null, null, data, null,
                null, null, null, null, null);
    }

    public static StreamEvent error(String message) {
        return new StreamEvent("error", null, null, null, null, null, null, message,
                null, null, null, null, null);
    }

    public String getType() { return type; }
    public String getToolName() { return toolName; }
    public String getInput() { return input; }
    public Long getLatencyMs() { return latencyMs; }
    public String getStatus() { return status; }
    public String getToken() { return token; }
    public ChatResponseData getData() { return data; }
    public String getErrorMessage() { return errorMessage; }
    public TextResponse getTextResponse() { return textResponse; }
    public Timeline getTimeline() { return timeline; }
    public Table getTable() { return table; }
    public Map<String, Object> getReferences() { return references; }
    public UUID getChatId() { return chatId; }
}
