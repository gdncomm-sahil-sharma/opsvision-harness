package com.opsvision.harness.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.opsvision.harness.model.entity.Conversation;
import com.opsvision.harness.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Reads chat memory directly from the {@code conversation} table so that
 * restarting the app no longer wipes per-session context.
 *
 * <p>Storage shape: one {@link Conversation} row per turn (existing model),
 * reconstructed on read as a flat {@code [user, assistant, user, assistant, ...]}
 * sequence ordered by {@code sequence_number}. The {@code conversationId}
 * passed by Spring AI is the chat session's UUID — same key as
 * {@code MessageChatMemoryAdvisor.conversationId(session.getId().toString())}.
 *
 * <p>{@link #saveAll} is intentionally a no-op: persistence is owned by
 * {@code AIAssistantService}, which writes a complete {@link Conversation}
 * (query, response summary, {@code references} → {@code context_data}) at
 * the end of each turn. Letting the advisor double-write would create drift
 * — two writers, two truths. Reads come from the same rows. Don't "fix"
 * this to call {@code save}; the asymmetry is the design.
 *
 * <p>If a prior turn produced {@code references} (key identifiers like a
 * pickPackage code or stock-trace UUID), they are prepended to the
 * {@link AssistantMessage} content as {@code "[Prior turn references: {...}]"}
 * so the LLM can ground follow-ups on the actual identifiers rather than
 * fabricating from prose.
 */
@Component
public class JpaChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaChatMemoryRepository.class);

    private final ConversationRepository conversationRepository;
    private final int maxTurns;

    public JpaChatMemoryRepository(ConversationRepository conversationRepository,
                                   @Value("${app.chat.memory.max-messages:20}") int maxMessages) {
        this.conversationRepository = conversationRepository;
        this.maxTurns = Math.max(1, maxMessages / 2);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> findByConversationId(String conversationId) {
        UUID sessionId = parseUuidOrNull(conversationId);
        if (sessionId == null) {
            return Collections.emptyList();
        }

        List<Conversation> recentDesc =
                conversationRepository.findRecentForMemoryReplay(sessionId, maxTurns);
        if (recentDesc.isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> out = new ArrayList<>(recentDesc.size() * 2);
        for (int i = recentDesc.size() - 1; i >= 0; i--) {
            Conversation c = recentDesc.get(i);
            out.add(new UserMessage(c.getQuery()));
            out.add(new AssistantMessage(buildAssistantContent(c)));
        }
        log.debug("Memory replay session={} turns={} messages={}",
                sessionId, recentDesc.size(), out.size());
        return out;
    }

    @Override
    public List<String> findConversationIds() {
        return Collections.emptyList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // No-op. AIAssistantService owns Conversation persistence.
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        // No-op. There is no UI surface that triggers chat-memory clear today.
        // If this is wired up later, decide whether to soft-delete the session
        // or hard-delete conversation rows — both have audit implications.
    }

    private String buildAssistantContent(Conversation c) {
        String response = c.getResponse() == null ? "" : c.getResponse();
        JsonNode refs = c.getContextData();
        if (refs == null || refs.isNull() || refs.isEmpty()) {
            return response;
        }
        return "[Prior turn references: " + refs.toString() + "]\n" + response;
    }

    private UUID parseUuidOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            log.debug("Non-UUID conversationId in chat memory: {}", s);
            return null;
        }
    }
}
