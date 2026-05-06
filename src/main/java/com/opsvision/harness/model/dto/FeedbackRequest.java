package com.opsvision.harness.model.dto;

/**
 * Body for {@code POST /api/chats/{chatId}/messages/{seq}/feedback}.
 *
 * <p>{@code helpful} is a primitive boolean — Jackson rejects null on a
 * primitive field with HTTP 400, which is the right behaviour: an explicit
 * thumbs-up/down is required.
 *
 * <p>{@code comment} is optional free-text. {@code SessionService} caps it
 * at 2000 chars (anything longer → 400) and trims to null when blank.
 *
 * <p>Resubmitting feedback for the same turn upserts — there is no
 * "withdraw feedback" path today.
 */
public class FeedbackRequest {

    private boolean helpful;
    private String comment;

    public FeedbackRequest() {}

    public FeedbackRequest(boolean helpful, String comment) {
        this.helpful = helpful;
        this.comment = comment;
    }

    public boolean isHelpful() {
        return helpful;
    }

    public void setHelpful(boolean helpful) {
        this.helpful = helpful;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
