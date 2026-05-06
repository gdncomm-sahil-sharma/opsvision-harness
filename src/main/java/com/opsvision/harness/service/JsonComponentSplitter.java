package com.opsvision.harness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.harness.model.dto.response.StreamEvent;
import com.opsvision.harness.model.dto.response.Table;
import com.opsvision.harness.model.dto.response.TextResponse;
import com.opsvision.harness.model.dto.response.Timeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stateful detector that watches a tokenized JSON stream and emits a
 * {@link StreamEvent} at the moment each top-level field of the
 * {@code ChatResponseData} root closes. So instead of forcing the SSE
 * client to assemble valid JSON objects from {@code assistant_token}
 * fragments, the BE publishes ready-to-render
 * {@code text_response_complete}, {@code timeline_complete},
 * {@code table_complete}, and {@code references_complete} events as soon
 * as each component's JSON terminates.
 *
 * <p>Implementation is hand-rolled brace/bracket counting (string-state
 * aware) instead of Jackson's non-blocking parser, because the only
 * boundaries we care about are top-level value closures — char-level
 * scanning over a {@link StringBuilder} is simpler than maintaining a
 * full token stream, and works on UTF-16 indices so we don't need byte
 * offset bookkeeping.
 *
 * <p>Tolerates leading/trailing markdown fences, whitespace, and null
 * values for optional fields (no event is emitted for a field whose value
 * is a scalar like {@code null}; the comma after the scalar resets
 * field-tracking so the next field is detected correctly).
 *
 * <p>Not thread-safe; one instance per stream.
 */
class JsonComponentSplitter {

    private static final Logger log = LoggerFactory.getLogger(JsonComponentSplitter.class);

    private final ObjectMapper mapper;
    private final StringBuilder buf = new StringBuilder();

    private int scanIdx = 0;
    private int depth = 0;
    private boolean inString = false;
    private boolean escapeNext = false;
    private String currentField = null;
    private int valueStart = -1;

    JsonComponentSplitter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    List<StreamEvent> consume(String token) {
        if (token == null || token.isEmpty()) {
            return List.of();
        }
        buf.append(token);
        List<StreamEvent> out = new ArrayList<>();
        for (int i = scanIdx; i < buf.length(); i++) {
            char c = buf.charAt(i);
            if (escapeNext) {
                escapeNext = false;
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    escapeNext = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{', '[' -> {
                    if (depth == 1 && currentField != null && valueStart == -1) {
                        valueStart = i;
                    }
                    depth++;
                }
                case '}', ']' -> {
                    depth--;
                    if (depth == 1 && currentField != null && valueStart != -1) {
                        String valueJson = buf.substring(valueStart, i + 1);
                        StreamEvent ev = buildEvent(currentField, valueJson);
                        if (ev != null) {
                            out.add(ev);
                        }
                        currentField = null;
                        valueStart = -1;
                    }
                }
                case ':' -> {
                    if (depth == 1 && currentField == null) {
                        currentField = lookbackFieldName(i);
                    }
                }
                case ',' -> {
                    if (depth == 1 && currentField != null && valueStart == -1) {
                        currentField = null;
                    }
                }
                default -> {
                }
            }
        }
        scanIdx = buf.length();
        return out;
    }

    private String lookbackFieldName(int colonIdx) {
        int e = colonIdx - 1;
        while (e >= 0 && Character.isWhitespace(buf.charAt(e))) {
            e--;
        }
        if (e < 0 || buf.charAt(e) != '"') {
            return null;
        }
        int s = e - 1;
        while (s >= 0 && buf.charAt(s) != '"') {
            if (buf.charAt(s) == '\\') {
                return null;
            }
            s--;
        }
        if (s < 0) {
            return null;
        }
        return buf.substring(s + 1, e);
    }

    private StreamEvent buildEvent(String fieldName, String valueJson) {
        try {
            return switch (fieldName) {
                case "textResponse" -> StreamEvent.textResponseComplete(
                        mapper.readValue(valueJson, TextResponse.class));
                case "timelines" -> StreamEvent.timelineComplete(
                        mapper.readValue(valueJson, Timeline.class));
                case "table" -> StreamEvent.tableComplete(
                        mapper.readValue(valueJson, Table.class));
                case "references" -> StreamEvent.referencesComplete(
                        mapper.readValue(valueJson,
                                mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)));
                default -> null;
            };
        } catch (Exception e) {
            log.warn("JsonComponentSplitter: failed to parse {}: {}", fieldName, e.getMessage());
            return null;
        }
    }
}
