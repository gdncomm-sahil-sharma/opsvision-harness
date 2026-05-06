package com.opsvision.harness.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One event in the SSE stream from POST /api/chat/stream. Discriminator is
 * {@code type}; only the fields relevant to that type are populated. Sent
 * over SSE as JSON in the {@code data:} field of each event.
 *
 * Types:
 *   tool_call_start  — toolName, input
 *   tool_call_end    — toolName, latencyMs, status (SUCCESS|FAILED|CACHE_HIT), errorMessage
 *   assistant_token  — token (incremental token chunk from the LLM)
 *   final            — data (full ChatResponseData parsed from the accumulated stream)
 *   error            — errorMessage
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamEvent {

    public enum Type { tool_call_start, tool_call_end, assistant_token, final_, error }

    private final String type;
    private final String toolName;
    private final String input;
    private final Long latencyMs;
    private final String status;
    private final String token;
    private final ChatResponseData data;
    private final String errorMessage;

    private StreamEvent(String type, String toolName, String input, Long latencyMs,
                        String status, String token, ChatResponseData data, String errorMessage) {
        this.type = type;
        this.toolName = toolName;
        this.input = input;
        this.latencyMs = latencyMs;
        this.status = status;
        this.token = token;
        this.data = data;
        this.errorMessage = errorMessage;
    }

    public static StreamEvent toolCallStart(String toolName, String input) {
        return new StreamEvent("tool_call_start", toolName, input, null, null, null, null, null);
    }

    public static StreamEvent toolCallEnd(String toolName, long latencyMs, String status, String errorMessage) {
        return new StreamEvent("tool_call_end", toolName, null, latencyMs, status, null, null, errorMessage);
    }

    public static StreamEvent assistantToken(String token) {
        return new StreamEvent("assistant_token", null, null, null, null, token, null, null);
    }

    public static StreamEvent finalResponse(ChatResponseData data) {
        return new StreamEvent("final", null, null, null, null, null, data, null);
    }

    public static StreamEvent error(String message) {
        return new StreamEvent("error", null, null, null, null, null, null, message);
    }

    public String getType() { return type; }
    public String getToolName() { return toolName; }
    public String getInput() { return input; }
    public Long getLatencyMs() { return latencyMs; }
    public String getStatus() { return status; }
    public String getToken() { return token; }
    public ChatResponseData getData() { return data; }
    public String getErrorMessage() { return errorMessage; }
}
