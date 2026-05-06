-- Per-turn LLM token usage and cost. Captured from
-- ChatResponse.getMetadata().getUsage() at the end of each chat turn so we
-- can roll up "what does a chat cost" without hammering OpenAI's billing
-- API. Cost is computed harness-side from a static rate table keyed by
-- model name; nullable so legacy rows and edge-case turns (errors, no
-- usage metadata) don't break.
ALTER TABLE conversation ADD COLUMN prompt_tokens INTEGER;
ALTER TABLE conversation ADD COLUMN completion_tokens INTEGER;
ALTER TABLE conversation ADD COLUMN total_tokens INTEGER;
ALTER TABLE conversation ADD COLUMN cost_usd NUMERIC(12, 6);
ALTER TABLE conversation ADD COLUMN model VARCHAR(64);
