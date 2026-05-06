package com.opsvision.harness.service;

import com.opsvision.harness.model.dto.response.ChatResponseData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an OpenAI {@link ResponseFormat} to harden the harness against
 * the Q8-class failure where the model emits valid-looking content but
 * mis-nests top-level fields and Jackson blows up on a missing brace.
 *
 * <p>Three modes, switchable via {@code app.chat.output-format}:
 *
 * <ul>
 *   <li><b>{@code json-object}</b> (default) — OpenAI guarantees the model's
 *       text output is valid JSON. Doesn't enforce schema, but every call
 *       returns parseable JSON. Eliminates the missing-brace failure mode.
 *       Compatible with the existing {@code Map<String, Object>} references
 *       field and {@code List<List<Object>>} table cells without code change.</li>
 *
 *   <li><b>{@code json-schema-strict}</b> — Full strict mode. OpenAI server-side
 *       enforces conformance to the {@link ChatResponseData} schema. Rejects
 *       free-form maps and {@code Object}-typed lists, so this requires
 *       constraining the DTO first. Off by default.</li>
 *
 *   <li><b>{@code text}</b> — Disable response_format entirely; rely solely
 *       on the prompt-coaxed schema (the M4 baseline). Use this if either
 *       of the above modes causes regressions.</li>
 * </ul>
 *
 * <p>The strict-schema path post-processes the {@link BeanOutputConverter}
 * output to add {@code additionalProperties:false} and full {@code required}
 * lists, which OpenAI strict mode demands and victools/jsonschema-generator
 * doesn't emit by default.
 */
@Component
public class StrictResponseFormatFactory {

    private static final Logger log = LoggerFactory.getLogger(StrictResponseFormatFactory.class);

    public enum Mode { JSON_OBJECT, JSON_SCHEMA_STRICT, TEXT }

    private final Mode mode;
    private volatile ResponseFormat cached;

    public StrictResponseFormatFactory(@Value("${app.chat.output-format:json-object}") String configured) {
        this.mode = parseMode(configured);
        log.info("StrictResponseFormatFactory: mode={}", mode);
    }

    private Mode parseMode(String s) {
        if (s == null) return Mode.JSON_OBJECT;
        return switch (s.trim().toLowerCase()) {
            case "json-schema-strict", "json_schema_strict", "strict" -> Mode.JSON_SCHEMA_STRICT;
            case "text", "off", "disabled" -> Mode.TEXT;
            default -> Mode.JSON_OBJECT;
        };
    }

    public Mode getMode() { return mode; }

    /**
     * Returns the configured response format, or {@code null} when the
     * mode is {@code TEXT} (caller skips setting response_format on the
     * options). Cached after first build.
     */
    public ResponseFormat get() {
        if (mode == Mode.TEXT) return null;
        if (cached != null) return cached;
        synchronized (this) {
            if (cached != null) return cached;
            cached = build();
            return cached;
        }
    }

    private ResponseFormat build() {
        if (mode == Mode.JSON_OBJECT) {
            return ResponseFormat.builder()
                    .type(ResponseFormat.Type.JSON_OBJECT)
                    .build();
        }
        // JSON_SCHEMA_STRICT
        BeanOutputConverter<ChatResponseData> converter = new BeanOutputConverter<>(ChatResponseData.class);
        Map<String, Object> schema = converter.getJsonSchemaMap();
        forceStrictCompatible(schema);
        ResponseFormat.JsonSchema js = ResponseFormat.JsonSchema.builder()
                .name("ChatResponseData")
                .schema(schema)
                .strict(true)
                .build();
        log.info("Built strict JSON schema for ChatResponseData ({} top-level props)",
                schema.containsKey("properties")
                        ? ((Map<?, ?>) schema.get("properties")).size() : 0);
        return ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(js)
                .build();
    }

    /**
     * Walk the schema tree, force {@code additionalProperties:false} on
     * every {@code type:object}, and put every declared property into
     * {@code required}. Strict mode requirements that the auto-generated
     * schema does not produce.
     */
    @SuppressWarnings("unchecked")
    private void forceStrictCompatible(Map<String, Object> node) {
        if (node == null) return;

        Object type = node.get("type");
        if ("object".equals(type)) {
            node.put("additionalProperties", false);
            Object propsObj = node.get("properties");
            if (propsObj instanceof Map<?, ?> props && !props.isEmpty()) {
                node.put("required", new ArrayList<>(props.keySet()));
                for (Object child : props.values()) {
                    if (child instanceof Map) forceStrictCompatible((Map<String, Object>) child);
                }
            } else {
                node.putIfAbsent("properties", new LinkedHashMap<>());
                node.putIfAbsent("required", new ArrayList<String>());
            }
        }

        Object items = node.get("items");
        if (items instanceof Map) {
            forceStrictCompatible((Map<String, Object>) items);
        } else if (items instanceof List<?> list) {
            for (Object e : list) {
                if (e instanceof Map) forceStrictCompatible((Map<String, Object>) e);
            }
        }

        for (String alt : new String[]{"anyOf", "oneOf", "allOf"}) {
            Object branch = node.get(alt);
            if (branch instanceof List<?> list) {
                for (Object e : list) {
                    if (e instanceof Map) forceStrictCompatible((Map<String, Object>) e);
                }
            }
        }
    }
}
