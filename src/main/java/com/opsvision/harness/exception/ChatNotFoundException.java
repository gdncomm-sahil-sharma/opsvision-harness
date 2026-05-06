package com.opsvision.harness.exception;

import java.util.UUID;

/**
 * Thrown by {@code SessionService} when a chatId can't be resolved for the
 * caller, including:
 * <ul>
 *   <li>{@code "missing"} — null chatId on a path that requires one</li>
 *   <li>{@code "not-found-or-not-owned"} — chat doesn't exist, or exists but
 *       belongs to a different user. We don't distinguish on the wire to
 *       avoid leaking existence to other users.</li>
 *   <li>{@code "archived"} — chat is in ARCHIVED status and the caller is
 *       trying to write to it. The user must unarchive first.</li>
 * </ul>
 *
 * <p>Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class ChatNotFoundException extends RuntimeException {

    private final UUID chatId;
    private final String reason;

    public ChatNotFoundException(UUID chatId, String reason) {
        super("chat not found (" + reason + "): " + chatId);
        this.chatId = chatId;
        this.reason = reason;
    }

    public UUID getChatId() {
        return chatId;
    }

    public String getReason() {
        return reason;
    }
}
