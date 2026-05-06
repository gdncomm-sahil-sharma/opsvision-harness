package com.opsvision.harness.model.dto;

/**
 * Body for {@code PATCH /api/chats/{chatId}}. {@code title} is required;
 * service rejects null/blank/over-255-char titles with 400.
 */
public class RenameChatRequest {
    private String title;

    public RenameChatRequest() {}

    public RenameChatRequest(String title) {
        this.title = title;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
