-- Multi-chat support
--
-- Adds a user-editable display title to each chat (session). NULL means
-- "no explicit title set"; the API derives a display title on the fly
-- from initial_query (whitespace-collapsed, truncated to 80 chars + '…').
-- Existing rows stay NULL on upgrade — no backfill needed.
--
-- ARCHIVED is added to SessionStatus on the Java side; session.status is
-- VARCHAR(50) with no CHECK constraint, so no DB change is needed there.

ALTER TABLE session ADD COLUMN title VARCHAR(255);
