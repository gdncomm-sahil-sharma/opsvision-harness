package com.opsvision.harness.model.dto.response;

import java.util.List;

public class TextResponse {
    private String summary;
    private List<String> bullets;

    public TextResponse() {}

    public TextResponse(String summary, List<String> bullets) {
        this.summary = summary;
        this.bullets = bullets;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getBullets() {
        return bullets;
    }

    public void setBullets(List<String> bullets) {
        this.bullets = bullets;
    }
}