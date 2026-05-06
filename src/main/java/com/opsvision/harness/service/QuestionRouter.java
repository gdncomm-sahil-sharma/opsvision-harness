package com.opsvision.harness.service;

import com.opsvision.harness.config.QuestionRouterConfig;
import com.opsvision.harness.config.QuestionRouterConfig.Rule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pre-classifies the user message via regex rules and emits routing
 * hints to inject into the system prompt before the LLM sees the
 * question.
 *
 * <p>The LLM is the only router today: every question goes through the
 * full system prompt + tool descriptions and the LLM picks. For obvious
 * shape-driven cases (a picker code → diagnosePickerQueue, a PP code
 * mislabeled "pick list" → diagnosePickPackage not getPickList), a
 * regex pre-classifier resolves the routing in one pass — saves a tool
 * call and stops the LLM from guessing wrong.
 *
 * <p>Match-all semantics: every rule whose pattern matches contributes
 * a hint. Multiple matches are concatenated; the LLM sees a single
 * "[ROUTING HINTS]" block prefixed to the system prompt.
 *
 * <p>Companion to {@link CriticalSignalInspector}: input-side hint
 * (here) versus output-side hint (there).
 */
@Component
public class QuestionRouter {

    private static final Logger log = LoggerFactory.getLogger(QuestionRouter.class);

    private final QuestionRouterConfig config;
    private final List<CompiledRule> compiled = new ArrayList<>();

    @Autowired
    public QuestionRouter(QuestionRouterConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void compileRules() {
        if (config.getRules() == null) return;
        for (Rule r : config.getRules()) {
            if (r.getPattern() == null || r.getHint() == null) {
                log.warn("QuestionRouter: skipping rule '{}' — missing pattern or hint", r.getId());
                continue;
            }
            try {
                compiled.add(new CompiledRule(r.getId(), Pattern.compile(r.getPattern()), r.getHint()));
            } catch (Exception e) {
                log.error("QuestionRouter: rule '{}' has invalid pattern '{}': {}",
                        r.getId(), r.getPattern(), e.getMessage());
            }
        }
        log.info("QuestionRouter: compiled {} rule(s)", compiled.size());
    }

    /**
     * Returns the routing-hint block to prepend to the system prompt, or
     * {@code null} if no rules matched. The block names the matched rule
     * ids so we can grep audit logs by router decision.
     */
    public String routeMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank() || compiled.isEmpty()) return null;
        Map<String, String> matchedHints = new LinkedHashMap<>();
        for (CompiledRule cr : compiled) {
            if (cr.pattern.matcher(userMessage).find()) {
                matchedHints.put(cr.id, cr.hint);
            }
        }
        if (matchedHints.isEmpty()) return null;

        StringBuilder block = new StringBuilder("[ROUTING HINTS — applied by harness pre-classifier]\n");
        for (Map.Entry<String, String> e : matchedHints.entrySet()) {
            block.append("• [").append(e.getKey()).append("] ").append(e.getValue().trim()).append('\n');
        }
        block.append("[/ROUTING HINTS]\n\n");
        log.info("QuestionRouter: matched {} rule(s): {}", matchedHints.size(), matchedHints.keySet());
        return block.toString();
    }

    private record CompiledRule(String id, Pattern pattern, String hint) {}
}
