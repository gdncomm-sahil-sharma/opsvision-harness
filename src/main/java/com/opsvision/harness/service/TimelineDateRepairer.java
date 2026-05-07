package com.opsvision.harness.service;

import com.opsvision.harness.model.dto.response.ChatResponseData;
import com.opsvision.harness.model.dto.response.Timeline;
import com.opsvision.harness.model.dto.response.TimelineItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server-side repair of {@link TimelineItem#getDate() timeline.date}
 * after the LLM produces a {@link ChatResponseData}.
 *
 * <p>Why: gpt-5.4-mini reliably puts the source ISO-8601 timestamp into
 * the timeline {@code description} field but its epoch-ms <em>math</em>
 * is off by a full year for 2026 dates (training-data cutoff predates
 * 2026; the model approximates from 2025 patterns and is consistently
 * 31_536_000_000 ms low). Rather than try to coax the model toward
 * correct arithmetic, we extract the ISO timestamp from the description
 * and overwrite the LLM's {@code date} value with
 * {@code Instant.parse(...).toEpochMilli()}. ISO-string parsing is
 * unambiguous and removes LLM math from the path entirely.
 *
 * <p>Repair policy:
 * <ul>
 *   <li>If description contains an ISO-8601 UTC timestamp ({@code Z} or
 *       offset suffix), that becomes the canonical value.</li>
 *   <li>If description has no parseable timestamp AND the LLM emitted
 *       {@code 0}, set {@code date} to {@code null} so the UI can render
 *       "—" instead of "Jan 1, 1970".</li>
 *   <li>Otherwise leave the LLM's value alone — it might be right.</li>
 * </ul>
 *
 * <p>This pairs with the prompt rule that tells the LLM to omit {@code date}
 * when no timestamp is known. Two layers of defense for the same bug
 * because either failing alone produces wrong-looking timestamps in the
 * UI, and the UI is what users see.
 */
@Component
public class TimelineDateRepairer {

    private static final Logger log = LoggerFactory.getLogger(TimelineDateRepairer.class);

    /**
     * Matches ISO-8601 timestamps with millisecond optional, UTC ({@code Z})
     * or numeric offset. Captures the full match so {@link Instant#parse(CharSequence)}
     * can consume it directly.
     */
    private static final Pattern ISO_TS = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})"
    );

    /**
     * Walk the response and repair every timeline item in place.
     * No-op when the response has no timelines.
     */
    public void repair(ChatResponseData data) {
        if (data == null) return;
        Timeline timeline = data.getTimelines();
        if (timeline == null || timeline.getData() == null) return;

        int replaced = 0;
        int cleared = 0;
        for (TimelineItem item : timeline.getData()) {
            Long extracted = extractEpochMs(item.getDescription());
            if (extracted != null) {
                Long previous = item.getDate();
                if (previous == null || !previous.equals(extracted)) {
                    item.setDate(extracted);
                    replaced++;
                }
                continue;
            }
            // No ISO in description. If the LLM emitted 0 (the Jan-1-1970
            // default for a missing primitive), null it out so the UI
            // renders a placeholder instead of an obviously-wrong date.
            if (item.getDate() != null && item.getDate() == 0L) {
                item.setDate(null);
                cleared++;
            }
        }

        if (replaced > 0 || cleared > 0) {
            log.info("TimelineDateRepairer: {} repaired from description, {} cleared from epoch-zero",
                    replaced, cleared);
        }
    }

    private Long extractEpochMs(String description) {
        if (description == null || description.isEmpty()) return null;
        Matcher m = ISO_TS.matcher(description);
        if (!m.find()) return null;
        try {
            return Instant.parse(m.group()).toEpochMilli();
        } catch (DateTimeParseException e) {
            log.debug("TimelineDateRepairer: failed to parse '{}': {}", m.group(), e.getMessage());
            return null;
        }
    }
}
