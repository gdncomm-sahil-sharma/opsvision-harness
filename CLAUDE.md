# OpsVision Harness — Quick Context

Spring Boot / Spring AI agent harness. Investigators ask warehouse questions over chat; the LLM calls MCP tools and returns structured responses (text + optional table + optional timeline + a `references` map for multi-turn continuity).

## Status

- **Stage**: pre-production hack project. UI is being built by a separate team. No real users yet.
- **Shipped on `feature/tech_fixes`**: per-session memory, tool-result cache, `tool_execution` audit, structured-output via `entity(ChatResponseData.class)`, SSE streaming (`/api/chat/stream`), MDC `[user=… conv=…]` log tagging, Loop 1 routing-hint nudge on empty MCP results, RecordingToolCallback with constructor-injected per-request context.
- **Parked**: eval harness, `JpaChatMemoryRepository`, plan-then-execute split, RAG, JSON-encoded production logging.

## Stack

- Java 21 + Spring Boot 4.0.6
- Spring AI 2.0.0-M5 (latest milestone — release notes: M5 added `maxCompletionTokens`)
- Default model: `gpt-5.4-mini` (configurable via `OPENAI_MODEL`). **Uses `max-completion-tokens`, not `max-tokens`** — gpt-5 family rejects the latter.
- MCP via `spring-ai-starter-mcp-client`, transport: streamable-http to `localhost:8081/mcp`
- Postgres + Redis (both local). The MCP server itself talks to a remote QA Postgres cluster.

## Running locally

1. **VPN required.** The MCP server talks to `central-postgres13-01.qa2-sg.cld` — corp-internal. If `nslookup` returns NXDOMAIN, you've dropped off VPN and every tool call will fail with `Could not open JDBC Connection for transaction`.
2. Postgres on `:5432` (db `opsvision`, user `opsvision`, password `opsvision`).
3. Redis on `:6379`.
4. MCP server at `:8081` — separate repo at `/Users/jimmy.nongmaithem/work/scps/opsvision-mcp-server`. Started with its own env vars (`STOCKHOLM_DB_PASSWORD`, `INVENTORY_DB_*`, `MOVEMENT_DB_*`).
5. `source setup-env.sh` (or export `OPENAI_API_KEY`, `DB_USERNAME`, `DB_PASSWORD`).
6. `mvn spring-boot:run` — app on `:8080`.

## Architecture sketch

- **Single chat service**: `AIAssistantService` drives Spring AI's tool-calling loop. Both `/api/chat` and `/api/chat/stream` call into it. Don't add a second chat service; consolidate here.
- **Per-request context** (conversationId, invokedTools, userId, optional SSE sink) is **constructor-injected** into `RecordingToolCallback` — not ThreadLocals. ThreadLocals don't propagate across Reactor schedulers; this previously broke streaming-path `tool_execution` persistence and log MDC. Don't reintroduce ThreadLocals for per-request state.
- **Cache** keyed by `sha256(toolName + args)`, 30 min TTL via `StringRedisTemplate`. Skips not-found results (`looksLikeEntityNotFound`) to avoid poisoning a turn that called the wrong tool.
- **Loop 1 nudge**: when MCP returns an empty entity, `augmentNotFoundResult` appends a generic routing hint so the LLM can self-correct in-loop (e.g. retry with `getPickPackage` after `getPickList` returned empty for a PP-shaped input). Generic across all tools.
- **Memory**: `MessageChatMemoryAdvisor` keyed by `session.id`. In-process — restarts wipe memory.
- **References**: `ChatResponseData.references` Map carries identifiers (`pickPackageCode`, `stockTraceIds`, etc.) so follow-ups like "yes pull stock trace" resolve the implicit subject without fabrication. The LLM populates it per-turn.

## Verification baseline

`~/Downloads/WCS phantom-close investigation — 2026-05-04.pdf` is the source of truth for the manual eval set: 8 single-turn questions plus a 3-turn flow (224143 → reconcile → stock trace). Latest baseline (Phase A): 7/8 strong, multi-turn working, Q6 partial closed via Loop 1.

## Known gaps (not bugs, just unfinished)

- `Conversation.context_data` JSONB column is never written. Either start writing `references` to it per turn, or drop the column.
- `MessageChatMemoryAdvisor` is in-process. App restart = lost conversation memory. `JpaChatMemoryRepository` reading/writing `Conversation` rows is the planned fix.
- `app.session.max-active-per-user=5` in `application.yml` doesn't match `SessionService.getOrCreateActiveSession()` returning exactly one. Either honor the config or make it explicit that there's at most one active session per user.

## Don't

- Don't add `max-tokens` config — gpt-5.4-mini rejects it. Use `max-completion-tokens`.
- Don't trust user wording over identifier shape ("look up pick list X" where X is a PP code is a known foot-gun — see `llm-prompt.txt`).
- Don't try to make the MCP server detect "wrong tool" calls — MCP is intent-blind. Wrong-tool detection lives harness-side, in Loop 1 nudges and post-hoc telemetry.
- Don't put log lines directly inside Reactor `doOn*` callbacks without scoping MDC via the `withMdc` helper. The chat scheduler thread doesn't carry MDC from the worker.
- Don't build an eval harness *until* there's production usage to seed it with. Eval cases curated in advance over-fit to imagination, not real failure modes.
- Don't pre-build a feedback-collection endpoint without somebody committed to reviewing the rows weekly. Feedback that nobody reads is data exhaust.
