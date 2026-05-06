-- LLM self-grading + per-turn user feedback.
--
-- The four columns below all live on `conversation` (one row per turn). The
-- service keeps them in sync; the LLM populates `answered`/`unanswered_reason`
-- via structured output, and the user populates `helpful`/`feedback_comment`
-- via POST /api/chats/{id}/messages/{seq}/feedback. NULL on legacy rows and
-- on rows where the relevant party hasn't acted (LLM forgot to grade, or user
-- never gave feedback). The memory-replay filter treats `answered=null` as
-- "still includable" so legacy rows continue to flow into LLM context — only
-- explicit `answered=false` is excluded.

ALTER TABLE conversation ADD COLUMN answered BOOLEAN;
ALTER TABLE conversation ADD COLUMN unanswered_reason TEXT;

-- Partial index for offline gap review: "show me every turn the LLM gave up
-- on, ordered by recency within a chat". Partial keeps the index tiny since
-- the dominant case is answered=true or NULL.
CREATE INDEX idx_conversation_unanswered ON conversation(session_id, created_at)
    WHERE answered = false;

ALTER TABLE conversation ADD COLUMN helpful BOOLEAN;
ALTER TABLE conversation ADD COLUMN feedback_comment TEXT;
