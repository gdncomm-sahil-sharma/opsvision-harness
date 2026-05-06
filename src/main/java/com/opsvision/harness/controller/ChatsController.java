package com.opsvision.harness.controller;

import com.opsvision.harness.model.dto.ChatMessageDto;
import com.opsvision.harness.model.dto.ChatSummaryDto;
import com.opsvision.harness.model.dto.RenameChatRequest;
import com.opsvision.harness.model.entity.Conversation;
import com.opsvision.harness.model.entity.Session;
import com.opsvision.harness.model.enums.SessionStatus;
import com.opsvision.harness.repository.ConversationRepository;
import com.opsvision.harness.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Resource-style API for chat lifecycle (sidebar list, message history,
 * rename, archive, unarchive). Companion to {@link SimpleChatController}
 * which still owns the verb-y {@code POST /api/chat[/stream]} endpoints.
 *
 * <p>Errors surface via {@link com.opsvision.harness.exception.GlobalExceptionHandler}:
 * {@code ChatNotFoundException} → 404, malformed UUID → 400, invalid title → 400.
 */
@RestController
@RequestMapping("/api/chats")
@CrossOrigin(origins = "*")
public class ChatsController {

    private static final Logger log = LoggerFactory.getLogger(ChatsController.class);

    @Autowired
    private SessionService sessionService;

    @Autowired
    private ConversationRepository conversationRepository;

    /**
     * Sidebar list. Defaults to {@code status=ACTIVE}; pass {@code status=ARCHIVED}
     * to view soft-deleted chats. {@code lastMessageAt} is computed per-row from
     * {@code conversation.created_at} — N+1 in volume, but chats-per-user is small
     * (sidebar, not feed); revisit if usage grows.
     */
    @GetMapping
    public List<ChatSummaryDto> list(@RequestParam String userId,
                                     @RequestParam(required = false) SessionStatus status) {
        List<Session> chats = sessionService.listChatsForUser(userId, status);
        return chats.stream().map(this::toSummary).toList();
    }

    /**
     * Full message history for a chat, oldest-first. Allowed regardless of
     * chat status — users can still read archived chats.
     */
    @GetMapping("/{chatId}/messages")
    public List<ChatMessageDto> messages(@PathVariable UUID chatId,
                                         @RequestParam String userId) {
        List<Conversation> rows = sessionService.getChatMessages(chatId, userId);
        return rows.stream().map(this::toMessage).toList();
    }

    /**
     * Rename. Allowed in any status (you might relabel an archived chat
     * before unarchiving). Trims whitespace; rejects null/blank/over-255.
     */
    @PatchMapping("/{chatId}")
    public ChatSummaryDto rename(@PathVariable UUID chatId,
                                 @RequestParam String userId,
                                 @RequestBody RenameChatRequest body) {
        Session updated = sessionService.renameChat(chatId, userId, body == null ? null : body.getTitle());
        log.info("Renamed chat {} to '{}'", chatId, updated.getTitle());
        return toSummary(updated);
    }

    /** Soft-delete. Idempotent. */
    @PostMapping("/{chatId}/archive")
    public ChatSummaryDto archive(@PathVariable UUID chatId,
                                  @RequestParam String userId) {
        return toSummary(sessionService.archiveChat(chatId, userId));
    }

    /** Restore archived chat to ACTIVE. Idempotent. Memory survives the cycle. */
    @PostMapping("/{chatId}/unarchive")
    public ChatSummaryDto unarchive(@PathVariable UUID chatId,
                                    @RequestParam String userId) {
        return toSummary(sessionService.unarchiveChat(chatId, userId));
    }

    private ChatSummaryDto toSummary(Session s) {
        LocalDateTime lastMessageAt = conversationRepository
                .findLatestCreatedAtForSession(s.getId())
                .orElse(s.getCreatedAt());
        return new ChatSummaryDto(
                s.getId(),
                s.getDisplayTitle(),
                s.getStatus(),
                s.getCreatedAt(),
                lastMessageAt
        );
    }

    private ChatMessageDto toMessage(Conversation c) {
        return new ChatMessageDto(
                c.getSequenceNumber(),
                c.getQuery(),
                c.getResponse(),
                c.getContextData(),
                c.getCreatedAt(),
                c.getAnswered(),
                c.getUnansweredReason(),
                c.getHelpful(),
                c.getFeedbackComment()
        );
    }
}
