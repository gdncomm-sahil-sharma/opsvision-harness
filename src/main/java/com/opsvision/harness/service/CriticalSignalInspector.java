package com.opsvision.harness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.harness.config.CriticalSignalsConfig;
import com.opsvision.harness.config.CriticalSignalsConfig.Condition;
import com.opsvision.harness.config.CriticalSignalsConfig.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Loop-1 critical-signal nudge: after a successful MCP tool call, scan
 * the response for "must surface" patterns (e.g. a PP returning 0
 * pickListAllocations with no terminal/post-pick signals — the
 * Stuck-in-HOLD picking_task_request that {@code diagnosePickPackage}
 * cannot see). When a rule matches, return a hint string that the
 * caller appends to the result before the LLM sees it.
 *
 * <p>Generalises the existing {@code augmentNotFoundResult} pattern:
 * not-found augments empty payloads, this augments substantive payloads
 * whose signal shape demands a follow-up tool call. Persistence happens
 * BEFORE this augmentation runs (in the caller), so the audit row in
 * {@code tool_execution.result} captures the unmodified MCP response.
 *
 * <p>Rule storage: {@link CriticalSignalsConfig} bound from
 * {@code critical-signals.rules:} in application.yml. Rules are
 * evaluated in declaration order; the first match wins.
 */
@Component
public class CriticalSignalInspector {

    private static final Logger log = LoggerFactory.getLogger(CriticalSignalInspector.class);

    private final CriticalSignalsConfig config;
    private final ObjectMapper objectMapper;

    @Autowired
    public CriticalSignalInspector(CriticalSignalsConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        int n = config.getRules() == null ? 0 : config.getRules().size();
        log.info("CriticalSignalInspector loaded with {} rule(s)", n);
    }

    /**
     * Returns the hint for the first matching rule, or empty if none match.
     * Failure to parse the result is treated as "no match" — the caller
     * returns the original result unchanged.
     */
    public Optional<String> inspect(String toolName, String rawResult) {
        if (config.getRules() == null || config.getRules().isEmpty()) return Optional.empty();
        if (toolName == null || rawResult == null || rawResult.isBlank()) return Optional.empty();

        JsonNode payload;
        try {
            JsonNode root = objectMapper.readTree(rawResult);
            payload = unwrapMcpEnvelope(root);
            if (payload == null) return Optional.empty();
        } catch (Exception e) {
            log.debug("CriticalSignalInspector: failed to parse {} result: {}", toolName, e.getMessage());
            return Optional.empty();
        }

        for (Rule rule : config.getRules()) {
            if (!toolName.equals(rule.getTool())) continue;
            if (matches(payload, rule)) {
                log.info("CriticalSignalInspector: rule '{}' matched on {}", rule.getId(), toolName);
                return Optional.ofNullable(rule.getHint());
            }
        }
        return Optional.empty();
    }

    /** MCP wraps tool output as {@code [{"type":"text","text":"<json>"}]}; unwrap to the inner JSON. */
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

    private boolean matches(JsonNode payload, Rule rule) {
        if (rule.getWhen() == null || rule.getWhen().isEmpty()) return false;
        for (Condition c : rule.getWhen()) {
            if (!conditionMatches(payload, c)) return false;
        }
        return true;
    }

    private boolean conditionMatches(JsonNode payload, Condition c) {
        if (c.getPath() == null) return false;
        JsonNode node = payload.at(c.getPath());

        if (c.getEmpty() != null) {
            boolean isEmpty = node.isMissingNode() || node.isNull()
                    || (node.isArray() && node.isEmpty())
                    || (node.isObject() && node.isEmpty())
                    || (node.isTextual() && node.asText().isEmpty());
            return c.getEmpty() == isEmpty;
        }

        if (c.getEquals() != null) {
            if (node.isMissingNode() || node.isNull()) return false;
            Object expected = c.getEquals();
            if (expected instanceof Boolean b) return node.isBoolean() && node.asBoolean() == b;
            if (expected instanceof Number n) return node.isNumber() && node.decimalValue().compareTo(
                    new java.math.BigDecimal(n.toString())) == 0;
            return node.asText().equals(expected.toString());
        }

        // Condition with neither equals nor empty configured — fail safely.
        return false;
    }
}
