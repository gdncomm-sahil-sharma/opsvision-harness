package com.opsvision.harness.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Static rate-table cost calculator for OpenAI token usage. Rates are
 * loaded from {@code app.token-cost.rates.<model-prefix>:} in
 * application.yml — keyed by model-id <em>prefix</em> so a versioned
 * model id like {@code gpt-5.4-mini-2026-04-12} resolves against the
 * configured {@code gpt-5.4-mini} entry without needing per-version
 * updates.
 *
 * <p>Resolution rule: longest matching prefix wins. Missing model ⇒
 * {@code null} cost (logged once, then silenced) so unknown models don't
 * crash the persistence path. Adding a new model is a one-line YAML
 * change followed by a restart.
 *
 * <p>Rates are USD per 1,000 tokens, matching OpenAI's published pricing
 * format. Computation: {@code (prompt × inputRate + completion × outputRate) / 1000}.
 */
@Component
@ConfigurationProperties(prefix = "app.token-cost")
public class TokenCostCalculator {

    private static final Logger log = LoggerFactory.getLogger(TokenCostCalculator.class);

    private Map<String, ModelRate> rates = new HashMap<>();

    /** Models we've already complained about — prevents log spam. */
    private final Map<String, Boolean> warnedModels = new HashMap<>();

    public Map<String, ModelRate> getRates() { return rates; }
    public void setRates(Map<String, ModelRate> rates) { this.rates = rates; }

    /**
     * Returns USD cost or {@code null} if the model is unknown / inputs
     * are null. Null is the right "missing" signal — callers persist a
     * NULL into {@code conversation.cost_usd} rather than rolling 0 into
     * cost aggregates.
     */
    public BigDecimal compute(String model, Integer promptTokens, Integer completionTokens) {
        if (model == null || promptTokens == null || completionTokens == null) return null;
        ModelRate rate = lookup(model);
        if (rate == null) return null;
        BigDecimal in = rate.getInputUsdPer1k().multiply(BigDecimal.valueOf(promptTokens));
        BigDecimal out = rate.getOutputUsdPer1k().multiply(BigDecimal.valueOf(completionTokens));
        return in.add(out).divide(BigDecimal.valueOf(1000), MathContext.DECIMAL64);
    }

    /** Longest-prefix-match against the configured rate table. */
    private ModelRate lookup(String model) {
        String best = null;
        for (String key : rates.keySet()) {
            if (model.startsWith(key) && (best == null || key.length() > best.length())) {
                best = key;
            }
        }
        if (best == null) {
            if (warnedModels.putIfAbsent(model, Boolean.TRUE) == null) {
                log.warn("No cost rate configured for model '{}' — cost_usd will be NULL. " +
                        "Add app.token-cost.rates.<prefix> to application.yml.", model);
            }
            return null;
        }
        return rates.get(best);
    }

    /** Per-model input/output rates, USD per 1,000 tokens. */
    public static class ModelRate {
        private BigDecimal inputUsdPer1k;
        private BigDecimal outputUsdPer1k;

        public BigDecimal getInputUsdPer1k() { return inputUsdPer1k; }
        public void setInputUsdPer1k(BigDecimal inputUsdPer1k) { this.inputUsdPer1k = inputUsdPer1k; }

        public BigDecimal getOutputUsdPer1k() { return outputUsdPer1k; }
        public void setOutputUsdPer1k(BigDecimal outputUsdPer1k) { this.outputUsdPer1k = outputUsdPer1k; }
    }
}
