package com.opsvision.harness.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Field order is significant: it controls the order properties appear in
 * the BeanOutputConverter-generated JSON schema, which the LLM follows when
 * emitting structured output. The streaming endpoint ({@code /api/chat/stream})
 * emits component-completion events in the same order, so the UI can render
 * textResponse → timelines → table without reshuffling.
 */
@JsonPropertyOrder({"textResponse", "timelines", "table", "references", "answered", "unansweredReason"})
public class ChatResponseData {
    @JsonProperty("textResponse")
    private TextResponse textResponse;

    @JsonProperty("timelines")
    private Timeline timelines;

    @JsonProperty("table")
    private Table table;

    /**
     * Key identifiers (stock trace UUIDs, pick package codes, task IDs, etc.)
     * surfaced from tool output so they survive into multi-turn context. The
     * LLM populates these per-tool-call so a follow-up like
     * "yes, pull the stock trace" can reference the prior turn's UUID
     * without fabrication.
     */
    @JsonProperty("references")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> references;

    /**
     * LLM self-grade: did this turn substantively answer the user's question?
     * Boxed Boolean so {@code null} (no grade — legacy or LLM forgot) stays
     * distinct from {@code false} (explicit "couldn't answer"). Written last
     * in the streamed JSON so the model grades after producing the body
     * (post-hoc grading is more honest than committing a verdict up front).
     */
    @JsonProperty("answered")
    private Boolean answered;

    /**
     * One short phrase naming the gap when {@link #answered} is {@code false}
     * (e.g. "no tool indexes by picker_id"). Omitted from the wire format
     * when null, which is the common case for {@code answered=true} turns.
     */
    @JsonProperty("unansweredReason")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String unansweredReason;

    public ChatResponseData() {}

    public ChatResponseData(TextResponse textResponse, Timeline timelines, Table table) {
        this(textResponse, timelines, table, null);
    }

    public ChatResponseData(TextResponse textResponse, Timeline timelines, Table table,
                            Map<String, Object> references) {
        this.textResponse = textResponse;
        this.timelines = timelines;
        this.table = table;
        this.references = references;
    }

    public TextResponse getTextResponse() {
        return textResponse;
    }

    public void setTextResponse(TextResponse textResponse) {
        this.textResponse = textResponse;
    }

    public Timeline getTimelines() {
        return timelines;
    }

    public void setTimelines(Timeline timelines) {
        this.timelines = timelines;
    }

    public Table getTable() {
        return table;
    }

    public void setTable(Table table) {
        this.table = table;
    }

    public Map<String, Object> getReferences() {
        return references;
    }

    public void setReferences(Map<String, Object> references) {
        this.references = references;
    }

    public Boolean getAnswered() {
        return answered;
    }

    public void setAnswered(Boolean answered) {
        this.answered = answered;
    }

    public String getUnansweredReason() {
        return unansweredReason;
    }

    public void setUnansweredReason(String unansweredReason) {
        this.unansweredReason = unansweredReason;
    }
}
