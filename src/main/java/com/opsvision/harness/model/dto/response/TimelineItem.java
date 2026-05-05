package com.opsvision.harness.model.dto.response;

public class TimelineItem {
    private long date;
    private String title;
    private String description;
    private TimelineStatus status;

    public TimelineItem() {}

    public TimelineItem(long date, String title, String description, TimelineStatus status) {
        this.date = date;
        this.title = title;
        this.description = description;
        this.status = status;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TimelineStatus getStatus() {
        return status;
    }

    public void setStatus(TimelineStatus status) {
        this.status = status;
    }
}