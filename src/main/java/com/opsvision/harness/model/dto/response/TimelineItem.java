package com.opsvision.harness.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One row on the per-PP timeline rendered by the UI.
 *
 * <p>{@code date} is boxed ({@link Long}, not {@code long}) and
 * {@link JsonInclude.Include#NON_NULL} so the wire format distinguishes
 * "no timestamp known" from "1970-01-01 00:00:00 UTC". A primitive long
 * defaults to {@code 0L} when the LLM omits the field, which the UI then
 * renders as "Jan 1, 1970" — a real bug we hit in production. Keeping it
 * boxed lets the UI render a placeholder ("—") for unknown dates, and
 * complements the prompt rule that forbids emitting {@code 0}.
 */
public class TimelineItem {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long date;
    private String title;
    private String description;
    private TimelineStatus status;

    public TimelineItem() {}

    public TimelineItem(Long date, String title, String description, TimelineStatus status) {
        this.date = date;
        this.title = title;
        this.description = description;
        this.status = status;
    }

    public Long getDate() {
        return date;
    }

    public void setDate(Long date) {
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
