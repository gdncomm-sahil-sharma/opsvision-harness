package com.opsvision.harness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * YAML-driven rules for the critical-signal inspector. Sourced from
 * {@code application.yml} under {@code critical-signals.rules:}. Each
 * rule names a tool, a list of conditions (every condition must match),
 * and a hint string appended to the tool result when the rule fires.
 *
 * <p>Matching is intentionally conservative: a missing path treats the
 * scalar comparison as a non-match and the {@code empty:true} comparison
 * as a match (missing == empty for our purposes). Either {@code equals}
 * OR {@code empty} per condition — not both.
 *
 * <p>Hot-reload is NOT supported here; rule changes require a restart.
 * Keeping the rules in {@code application.yml} mirrors the prompt-file
 * iteration cadence (also classpath-loaded at boot), which is the
 * comparable touch-point we expect operators to edit.
 */
@Configuration
@ConfigurationProperties(prefix = "critical-signals")
public class CriticalSignalsConfig {

    private List<Rule> rules = new ArrayList<>();

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static class Rule {
        private String id;
        private String tool;
        private List<Condition> when = new ArrayList<>();
        private String hint;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTool() { return tool; }
        public void setTool(String tool) { this.tool = tool; }

        public List<Condition> getWhen() { return when; }
        public void setWhen(List<Condition> when) { this.when = when; }

        public String getHint() { return hint; }
        public void setHint(String hint) { this.hint = hint; }
    }

    public static class Condition {
        private String path;
        /** Boxed so we can distinguish "not configured" from "configured to false". */
        private Object equals;
        private Boolean empty;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public Object getEquals() { return equals; }
        public void setEquals(Object equals) { this.equals = equals; }

        public Boolean getEmpty() { return empty; }
        public void setEmpty(Boolean empty) { this.empty = empty; }
    }
}
