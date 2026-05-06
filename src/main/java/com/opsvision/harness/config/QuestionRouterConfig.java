package com.opsvision.harness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * YAML-driven rules for the question pre-classifier router. Sourced from
 * {@code application.yml} under {@code question-router.rules:}. Each rule
 * matches a regex against the user's message; matching rules contribute
 * a {@code hint} string that the harness injects into the system prompt
 * before the LLM sees the message.
 *
 * <p>This is the input-side counterpart to the critical-signal inspector
 * (output-side, post-tool). Together they handle question-shape correction
 * and tool-result correction without requiring prompt-LLM gymnastics.
 *
 * <p>Hot-reload not supported; restart to pick up rule changes.
 */
@Configuration
@ConfigurationProperties(prefix = "question-router")
public class QuestionRouterConfig {

    private List<Rule> rules = new ArrayList<>();

    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> rules) { this.rules = rules; }

    public static class Rule {
        private String id;
        /** Java {@link java.util.regex.Pattern} syntax. Use {@code (?i)} prefix for case-insensitive. */
        private String pattern;
        private String hint;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }

        public String getHint() { return hint; }
        public void setHint(String hint) { this.hint = hint; }
    }
}
