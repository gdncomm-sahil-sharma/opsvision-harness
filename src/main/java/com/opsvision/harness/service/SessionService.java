package com.opsvision.harness.service;

import com.opsvision.harness.exception.ChatNotFoundException;
import com.opsvision.harness.model.entity.Conversation;
import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.enums.SessionStatus;
import com.opsvision.harness.repository.ConversationRepository;
import com.opsvision.harness.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Owns the lifecycle of a "chat" — represented internally by the {@code Session}
 * entity. Resolves a chat for an inbound message (creating one when no chatId
 * was supplied), enforces ownership (users can only touch their own chats),
 * and powers the sidebar surface (list / rename / archive / unarchive).
 *
 * <p>Ownership and status checks are centralised in {@link #requireOwnedChatAnyStatus}
 * and {@link #requireOwnedChatActive}. Controllers translate HTTP and let
 * {@link ChatNotFoundException} bubble; {@code GlobalExceptionHandler} maps
 * it to 404 without distinguishing "wrong user" from "doesn't exist" (so we
 * don't leak chat existence across users).
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    /**
     * Resolve the chat an inbound message should land in. {@code chatId} is
     * optional:
     * <ul>
     *   <li>null → create a new ACTIVE chat with {@code initialQuery} as the
     *       first message (also used to derive the default display title)</li>
     *   <li>non-null → must belong to {@code userId} and be ACTIVE; otherwise
     *       throws {@link ChatNotFoundException}. Archived chats are
     *       deliberately rejected — clients must explicitly unarchive first.</li>
     * </ul>
     */
    public Session getOrCreateChat(String userId, UUID chatId, String initialQuery) {
        if (chatId == null) {
            Session created = sessionRepository.save(
                    new Session(userId, initialQuery, SessionStatus.ACTIVE));
            log.info("Created new chat {} for user '{}'", created.getId(), userId);
            return created;
        }
        return requireOwnedChatActive(chatId, userId);
    }

    /**
     * Sidebar list. {@code status} defaults to {@link SessionStatus#ACTIVE}
     * when null — archived chats are hidden from the default view.
     */
    public List<Session> listChatsForUser(String userId, SessionStatus status) {
        SessionStatus filter = status != null ? status : SessionStatus.ACTIVE;
        return sessionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, filter);
    }

    /**
     * Read-only access to message history. Allowed regardless of chat status
     * — users can still view archived conversations.
     */
    public List<Conversation> getChatMessages(UUID chatId, String userId) {
        requireOwnedChatAnyStatus(chatId, userId);
        return conversationRepository.findBySessionIdOrderBySequenceNumberAsc(chatId);
    }

    /**
     * Rename. Allowed in any status (you might want to relabel an archived
     * chat before unarchiving it). {@code newTitle} is trimmed before saving;
     * null/blank/over-255-char rejected with {@link IllegalArgumentException}
     * (mapped to 400 by the global exception handler).
     */
    public Session renameChat(UUID chatId, String userId, String newTitle) {
        if (newTitle == null) {
            throw new IllegalArgumentException("title is required");
        }
        String trimmed = newTitle.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("title must be 255 characters or fewer");
        }
        Session chat = requireOwnedChatAnyStatus(chatId, userId);
        chat.setTitle(trimmed);
        return sessionRepository.save(chat);
    }

    /**
     * Soft-delete. Idempotent: archiving an already-archived chat returns
     * the chat unchanged.
     */
    public Session archiveChat(UUID chatId, String userId) {
        Session chat = requireOwnedChatAnyStatus(chatId, userId);
        if (chat.getStatus() != SessionStatus.ARCHIVED) {
            chat.setStatus(SessionStatus.ARCHIVED);
            chat = sessionRepository.save(chat);
            log.info("Archived chat {} for user '{}'", chat.getId(), userId);
        }
        return chat;
    }

    /**
     * Restore an archived chat to ACTIVE. Idempotent. Memory survives the
     * archive cycle — the {@code MessageChatMemoryAdvisor} keys off
     * {@code session.id}, so the LLM resumes with the chat's prior context
     * intact.
     */
    public Session unarchiveChat(UUID chatId, String userId) {
        Session chat = requireOwnedChatAnyStatus(chatId, userId);
        if (chat.getStatus() != SessionStatus.ACTIVE) {
            chat.setStatus(SessionStatus.ACTIVE);
            chat = sessionRepository.save(chat);
            log.info("Unarchived chat {} for user '{}'", chat.getId(), userId);
        }
        return chat;
    }

    /**
     * Ownership check that ignores status. Used by read endpoints
     * ({@code /messages}) and lifecycle endpoints (rename, archive, unarchive)
     * where archived chats remain accessible.
     */
    private Session requireOwnedChatAnyStatus(UUID chatId, String userId) {
        if (chatId == null) {
            throw new ChatNotFoundException(null, "missing");
        }
        return sessionRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new ChatNotFoundException(chatId, "not-found-or-not-owned"));
    }

    /**
     * Stricter check used by the chat-write path: chat must exist, be owned
     * by the user, AND be ACTIVE. Archived chats are read-only.
     */
    private Session requireOwnedChatActive(UUID chatId, String userId) {
        Session chat = requireOwnedChatAnyStatus(chatId, userId);
        if (chat.getStatus() != SessionStatus.ACTIVE) {
            throw new ChatNotFoundException(chatId, "archived");
        }
        return chat;
    }
}
