package com.opsvision.harness.model.dto.response;

import java.util.List;

public class Timeline {
    private String title;
    private List<TimelineItem> data;

    public Timeline() {}

    public Timeline(String title, List<TimelineItem> data) {
        this.title = title;
        this.data = data;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<TimelineItem> getData() {
        return data;
    }

    public void setData(List<TimelineItem> data) {
        this.data = data;
    }
}