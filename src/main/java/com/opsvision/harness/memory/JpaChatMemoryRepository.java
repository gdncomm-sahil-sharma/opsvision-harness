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
 * <p>Replay sizing — two caps, both applied:
 * <ol>
 *   <li>{@code app.chat.memory.max-messages} — hard cap on raw turn count
 *       (preserves the original config knob).</li>
 *   <li>{@code app.chat.memory.max-tokens} — soft cap on context size.
 *       The replay walks newest-first, accumulates a cheap char/4 token
 *       estimate, and drops any tail that would overflow the budget. This
 *       matters more than message count because a single turn with a 10-row
 *       table replayed verbatim is several long messages' worth of tokens.</li>
 * </ol>
 *
 * <p>Replay format: each prior turn renders as a structured assistant
 * message that clearly separates self-grade, references, and prose.
 * Earlier versions concatenated the references map as raw JSON before the
 * summary; newer versions tag the line so the LLM can parse the structure
 * if it wants to ("[refs] {...}\n[answered] true\n<summary>").
 *
 * <p>{@link #saveAll} is intentionally a no-op: persistence is owned by
 * {@code AIAssistantService}, which writes a complete {@link Conversation}
 * (query, response summary, {@code references} → {@code context_data}) at
 * the end of each turn. Letting the advisor double-write would create drift
 * — two writers, two truths. Reads come from the same rows. Don't "fix"
 * this to call {@code save}; the asymmetry is the design.
 */
@Component
public class JpaChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaChatMemoryRepository.class);

    private final ConversationRepository conversationRepository;
    private final int maxTurns;
    private final int maxTokens;

    public JpaChatMemoryRepository(ConversationRepository conversationRepository,
                                   @Value("${app.chat.memory.max-messages:20}") int maxMessages,
                                   @Value("${app.chat.memory.max-tokens:4000}") int maxTokens) {
        this.conversationRepository = conversationRepository;
        this.maxTurns = Math.max(1, maxMessages / 2);
        this.maxTokens = Math.max(500, maxTokens);
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

        // Token-budget walk: drop oldest turns first if we'd overflow.
        // recentDesc is newest-first; we accumulate forward and keep a
        // prefix of newest turns whose total stays under budget.
        List<Conversation> kept = new ArrayList<>(recentDesc.size());
        int tokenAcc = 0;
        for (Conversation c : recentDesc) {
            int turnTokens = estimateTokens(c.getQuery())
                    + estimateTokens(buildAssistantContent(c));
            if (!kept.isEmpty() && tokenAcc + turnTokens > maxTokens) break;
            kept.add(c);
            tokenAcc += turnTokens;
        }

        List<Message> out = new ArrayList<>(kept.size() * 2);
        for (int i = kept.size() - 1; i >= 0; i--) {
            Conversation c = kept.get(i);
            out.add(new UserMessage(c.getQuery()));
            out.add(new AssistantMessage(buildAssistantContent(c)));
        }

        if (kept.size() < recentDesc.size()) {
            log.info("Memory replay session={} turns={}/{} messages={} est_tokens={} (token-cap dropped {} oldest)",
                    sessionId, kept.size(), recentDesc.size(), out.size(), tokenAcc,
                    recentDesc.size() - kept.size());
        } else {
            log.debug("Memory replay session={} turns={} messages={} est_tokens={}",
                    sessionId, kept.size(), out.size(), tokenAcc);
        }
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

    /**
     * Build the assistant-side content the LLM sees on replay. Structured
     * tagging — refs / answered / summary on separate lines — so the model
     * can parse the prior turn's identifiers without confusing them with
     * prose.
     */
    private String buildAssistantContent(Conversation c) {
        String summary = c.getResponse() == null ? "" : c.getResponse();
        StringBuilder sb = new StringBuilder();
        JsonNode refs = c.getContextData();
        if (refs != null && !refs.isNull() && !refs.isEmpty()) {
            sb.append("[refs] ").append(refs.toString()).append('\n');
        }
        if (Boolean.FALSE.equals(c.getAnswered())) {
            sb.append("[answered] false");
            if (c.getUnansweredReason() != null && !c.getUnansweredReason().isBlank()) {
                sb.append(" — ").append(c.getUnansweredReason());
            }
            sb.append('\n');
        }
        sb.append(summary);
        return sb.toString();
    }

    /**
     * Cheap token estimate. OpenAI's BPE tokenizer averages roughly 4
     * characters per token for English; we use that as an approximation
     * to avoid a tokenizer dependency on the read path. Off by 10-20% is
     * acceptable here — this is a budget guard, not billing.
     */
    private int estimateTokens(String s) {
        if (s == null || s.isEmpty()) return 0;
        return Math.max(1, s.length() / 4);
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
