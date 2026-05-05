package com.opsvision.harness.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatResponseData {
    @JsonProperty("textResponse")
    private TextResponse textResponse;
    
    @JsonProperty("timelines")
    private Timeline timelines;
    
    @JsonProperty("table")
    private Table table;

    public ChatResponseData() {}

    public ChatResponseData(TextResponse textResponse, Timeline timelines, Table table) {
        this.textResponse = textResponse;
        this.timelines = timelines;
        this.table = table;
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
}