# API Contracts

HTTP API for the OpsVision investigation harness. Covers chat (single-shot + streaming) and chat lifecycle (sidebar, rename, archive).

- Base URL (dev): `http://localhost:8080`
- Auth: none in this stack. `userId` is a logical owner key passed by the client (request body for POSTs, query string for GETs/PATCHes).
- All bodies are JSON unless noted. Streaming endpoint uses `text/event-stream`.

---

## Endpoints at a glance

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/chat` | Send one message; returns full structured response. |
| `POST` | `/api/chat/stream` | Same, but Server-Sent Events with progressive component delivery. |
| `GET` | `/api/chats?userId=…&status=…` | Sidebar list. Defaults to `status=ACTIVE`. |
| `GET` | `/api/chats/{chatId}/messages?userId=…` | Full turn history, oldest-first. |
| `PATCH` | `/api/chats/{chatId}?userId=…` | Rename (set title). |
| `POST` | `/api/chats/{chatId}/archive?userId=…` | Soft-delete. Idempotent. |
| `POST` | `/api/chats/{chatId}/unarchive?userId=…` | Restore to ACTIVE. Idempotent. |
| `POST` | `/api/chats/{chatId}/messages/{seq}/feedback?userId=…` | User feedback on a turn (thumbs + comment). Upsert. |

---

## `POST /api/chat`

Send one message synchronously. Creates a new chat when `chatId` is omitted.

### Request

```json
{
  "userId": "dev-user",
  "chatId": "7bfb5cf9-8608-4038-97fd-bca6fe3393f9",
  "message": "What happened with order 224143?"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `userId` | string | yes | Logical owner key. |
| `chatId` | UUID | no | Omit/null to start a new chat. |
| `message` | string | yes | The user's prompt. |

### Response — `200 OK`

```json
{
  "status": 200,
  "success": true,
  "data": {
    "textResponse":     { "summary": "...", "bullets": ["..."] },
    "timelines":        { "title": "...", "data": [ /* TimelineItem */ ] },
    "table":            { "title": "...", "headers": ["..."], "data": [["..."]] },
    "references":       { "stockTraceId": "..." },
    "answered":         true,
    "unansweredReason": null
  },
  "timestamp": 1778083803123,
  "chatId": "7bfb5cf9-8608-4038-97fd-bca6fe3393f9"
}
```

- `data.*` UI components may be omitted when not relevant for the question.
- Field order inside `data` is fixed: `textResponse → timelines → table → references → answered → unansweredReason` (matches streaming order and UI render order; grade fields trail so the LLM grades post-hoc).
- `chatId` echoes the chat the message landed in — the existing one if you supplied a `chatId`, the newly-created one otherwise. Store it for the next request.
- `answered` is the LLM's self-grade — `true` when the response is grounded in tool output, `false` when no available tool could resolve the question, every relevant tool returned empty, the LLM had to refuse, or it ended up speculating. `null` only on legacy rows or when the model failed to grade.
- `unansweredReason` is a short phrase naming the gap when `answered=false` (e.g. `"no tool indexes by picker_id"`). Omitted from the wire format when null.
- The grade is for offline gap analysis; UIs don't need to render it (and the LLM is instructed not to mention it). It feeds into memory-replay filtering automatically — `answered=false` rows are excluded from the next turn's LLM context.

### Status codes

| Code | When |
|---|---|
| `200` | Normal success. |
| `400` | Malformed `chatId` UUID (response: malformed-uuid envelope). |
| `404` | `chatId` missing/not-owned/ARCHIVED (response: chat-not-found envelope). |
| `500` | LLM or internal error. Body still wraps a fallback `data`; `success: false`. |

---

## `POST /api/chat/stream`

SSE variant. Same request body. Emits a sequence of typed events as the LLM works.

### Response

Content-Type: `text/event-stream`. Each event:

```
event: <type>
data: <json>

```

#### Event types (in order)

| Type | Payload | Notes |
|---|---|---|
| `chat_id` | `{"type":"chat_id","chatId":"<uuid>"}` | **Always first.** Fires before any tool/token event so the UI can update its store/URL immediately. |
| `tool_call_start` | `{"type":"tool_call_start","toolName":"...","input":"<json>"}` | One per MCP tool invocation. |
| `tool_call_end` | `{"type":"tool_call_end","toolName":"...","latencyMs":N,"status":"SUCCESS\|FAILED\|CACHE_HIT","errorMessage":"..."}` | Pairs with `tool_call_start`. |
| `assistant_token` | `{"type":"assistant_token","token":"<raw json fragment>"}` | Raw JSON fragments. **Ignore unless you need typewriter UX** — components are surfaced via the `*_complete` events below. |
| `text_response_complete` | `{"type":"text_response_complete","textResponse":{...}}` | Fires when the `textResponse` JSON closes. Render the summary card here. |
| `timeline_complete` | `{"type":"timeline_complete","timeline":{...}}` | Fires when `timelines` closes. |
| `table_complete` | `{"type":"table_complete","table":{...}}` | Fires when `table` closes. |
| `references_complete` | `{"type":"references_complete","references":{...}}` | Fires when `references` closes. May be `{}`. Stash for the next turn. |
| `final` | `{"type":"final","data":{...full ChatResponseData...}}` | Safety net; idempotent with prior `*_complete`. Useful for late-joining renderers. |
| `error` | `{"type":"error","errorMessage":"..."}` | Stream may complete after this event. Includes `chat not found (...)` cases (since 404 isn't possible mid-stream). |

#### Order guarantees

```
chat_id → (tool_call_start tool_call_end)* → text_response_complete → timeline_complete → table_complete → references_complete → final
```

`assistant_token` events are interleaved between `tool_call_end` and `final` but, as noted, are not needed for component rendering.

The LLM also emits scalar `answered` and `unansweredReason` fields after `references` — these don't get their own `*_complete` event (they're not structured components), but they show up in the `final` event's `data` payload. UIs that want to react to a `answered=false` turn can read them from `final.data.answered` / `final.data.unansweredReason`.

### UI integration sketch (vanilla JS)

```js
async function streamChat(userId, chatId, message, handlers) {
  const res = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
    body: JSON.stringify({ userId, chatId, message })
  });
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buf = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });
    const blocks = buf.split('\n\n');
    buf = blocks.pop();
    for (const block of blocks) {
      const t = /^event:\s*(.+)$/m.exec(block)?.[1]?.trim();
      const d = /^data:\s*(.+)$/m.exec(block)?.[1];
      if (!t || !d) continue;
      handlers[t]?.(JSON.parse(d));
    }
  }
}

streamChat('dev', currentChatId, 'continue', {
  chat_id:                e => updateUrlAndStore(e.chatId),
  tool_call_start:        e => showToolBadge(e.toolName),
  tool_call_end:          e => hideToolBadge(e.toolName, e.status),
  text_response_complete: e => renderSummary(e.textResponse),
  timeline_complete:      e => renderTimeline(e.timeline),
  table_complete:         e => renderTable(e.table),
  references_complete:    e => stashRefs(e.references),
  final:                  e => hydrateFromFinal(e.data),
  error:                  e => showError(e.errorMessage),
});
```

> Use `fetch` + `ReadableStream`, not `EventSource` — `EventSource` is GET-only.

---

## `GET /api/chats?userId=…&status=…`

Sidebar list.

### Query params

| Param | Type | Required | Default |
|---|---|---|---|
| `userId` | string | yes | — |
| `status` | enum | no | `ACTIVE` |

Valid `status` values: `ACTIVE`, `ARCHIVED`, `COMPLETED`, `FAILED`, `EXPIRED`. UI typically only uses `ACTIVE` and `ARCHIVED`.

### Response — `200 OK`

```json
[
  {
    "chatId": "7bfb5cf9-8608-4038-97fd-bca6fe3393f9",
    "title": "Order 224143 investigation",
    "status": "ACTIVE",
    "createdAt": "2026-05-06T23:10:03.942691",
    "lastMessageAt": "2026-05-06T23:10:31.73129"
  }
]
```

| Field | Notes |
|---|---|
| `title` | User-set title if `PATCH`ed; otherwise derived from the first message (whitespace-collapsed, truncated to 80 chars + `…`). |
| `lastMessageAt` | Most recent turn's `created_at`. Falls back to chat's `createdAt` for empty chats. |
| `status` | One of the enum values. |

Sorted newest-first by `createdAt`.

### Example

```bash
curl 'http://localhost:8080/api/chats?userId=dev'
curl 'http://localhost:8080/api/chats?userId=dev&status=ARCHIVED'
```

---

## `GET /api/chats/{chatId}/messages?userId=…`

Full turn history, oldest-first. Allowed regardless of chat status — you can still read archived chats.

### Response — `200 OK`

```json
[
  {
    "sequence": 1,
    "query": "What happened with order 224143?",
    "response": "Order/pick package PK/MAR-01/V-2026/224143 is stalled…",
    "references": { "stockTraceId": "..." },
    "createdAt": "2026-05-06T23:10:03.976848",
    "answered": true,
    "unansweredReason": null,
    "helpful": false,
    "feedbackComment": "actually wrong on the bin reservation half"
  }
]
```

| Field | Notes |
|---|---|
| `sequence` | 1-indexed turn number within the chat. |
| `response` | May be `null` for a turn that errored mid-flight or whose stream is still in progress. |
| `references` | Parsed JSON object from the LLM's `references` map for that turn (may be null/empty). |
| `answered` | LLM self-grade. `null` on legacy rows or rows where the LLM forgot to grade. `false` means the LLM admitted it couldn't substantively answer. |
| `unansweredReason` | Short phrase naming the gap when `answered=false`. Omitted when null. |
| `helpful` | User-submitted thumbs (true/false) via the feedback endpoint. `null` when no feedback was given. |
| `feedbackComment` | Optional free-text feedback from the user. `null` when no feedback or no comment. |

All four grade/feedback fields use `@JsonInclude(NON_NULL)` — they're omitted from the wire format when null, which is the common case for ungraded/un-rated turns.

### Status codes

| Code | When |
|---|---|
| `200` | Success. |
| `400` | Malformed `chatId` UUID. |
| `404` | Chat doesn't exist or is owned by another user (responses are indistinguishable on purpose). |

---

## `PATCH /api/chats/{chatId}?userId=…`

Rename a chat. Allowed in any status.

### Request

```json
{ "title": "Order 224143 investigation" }
```

| Field | Type | Constraints |
|---|---|---|
| `title` | string | Required. Non-blank after trim. Max 255 chars. |

### Response — `200 OK` — `ChatSummaryDto`

Same shape as a list row. Returns the updated chat.

### Status codes

| Code | When |
|---|---|
| `200` | Success. |
| `400` | Title null/blank/over-255. |
| `404` | Chat not found or not owned. |

---

## `POST /api/chats/{chatId}/archive?userId=…`

Soft-delete. Hides the chat from the default sidebar (`status=ACTIVE`). Idempotent — archiving an already-archived chat returns 200 with no change.

### Response — `200 OK` — `ChatSummaryDto` with `status: "ARCHIVED"`.

---

## `POST /api/chats/{chatId}/unarchive?userId=…`

Restore an archived chat to `ACTIVE`. Idempotent.

**Memory survives the archive cycle.** When you post the next message after unarchive, the LLM resumes with full prior context — the chat memory advisor keys off `chatId` regardless of status.

### Response — `200 OK` — `ChatSummaryDto` with `status: "ACTIVE"`.

---

## `POST /api/chats/{chatId}/messages/{seq}/feedback?userId=…`

Submit per-turn user feedback (thumbs up/down + optional free-text comment). Resubmitting upserts onto the same row — there is no separate "withdraw" or "edit" path; just submit again with the new state. Feedback is for offline gap review only and is **not threaded into the LLM's context** on subsequent turns.

Allowed regardless of chat status, including ARCHIVED — feedback is metadata about a past turn (read-side concern), symmetric with `/messages` access.

### Request

```json
{
  "helpful": false,
  "comment": "actually wrong on the bin reservation half"
}
```

| Field | Type | Constraints |
|---|---|---|
| `helpful` | boolean | Required. `true` = thumbs-up, `false` = thumbs-down. Missing field → 400 (Jackson rejects null on a primitive). |
| `comment` | string | Optional. Trimmed; blank/empty stored as null. Max 2000 chars. |

### Response — `200 OK` — `ChatMessageDto`

The updated turn (same shape as a row from `/messages`), so the UI can swap the row in place after a successful submission.

```json
{
  "sequence": 1,
  "query": "...",
  "response": "...",
  "references": {...},
  "createdAt": "...",
  "answered": true,
  "helpful": false,
  "feedbackComment": "actually wrong on the bin reservation half"
}
```

### Status codes

| Code | When |
|---|---|
| `200` | Feedback recorded (whether new or upsert). |
| `400` | Body missing, missing `helpful` field, or `comment` longer than 2000 chars. |
| `404` | Chat not found / not owned (existence-leak-safe), or `seq` out of range for this chat (`reason="turn-not-found"`). |

### Examples

```bash
# Thumbs-up the second turn
curl -X POST 'http://localhost:8080/api/chats/$CHAT/messages/2/feedback?userId=alice' \
  -H 'Content-Type: application/json' \
  -d '{"helpful":true,"comment":"clean answer cited the trace UUID"}'

# Resubmit (upsert) — flip to thumbs-down
curl -X POST 'http://localhost:8080/api/chats/$CHAT/messages/2/feedback?userId=alice' \
  -H 'Content-Type: application/json' \
  -d '{"helpful":false,"comment":"actually wrong on the bin reservation"}'
```

---

## Error envelopes

### `404` — chat not found

```json
{
  "error": "chat not found",
  "reason": "missing",
  "chatId": "7bfb5cf9-8608-4038-97fd-bca6fe3393f9"
}
```

| `reason` | Meaning |
|---|---|
| `missing` | A path required a `chatId` but it was null. (Internal — clients shouldn't see this from the wire.) |
| `not-found-or-not-owned` | Chat doesn't exist, OR exists but belongs to a different `userId`. We deliberately don't distinguish on the wire to avoid leaking existence across users. |
| `archived` | Chat is `ARCHIVED` and the request was a write (`POST /api/chat`/`/stream` with this `chatId`). Unarchive first. |
| `turn-not-found` | The `seq` in `/messages/{seq}/feedback` doesn't exist for this chat. |

### `400` — malformed UUID

```json
{ "error": "malformed chatId", "value": "not-a-uuid" }
```

### `400` — bad request (e.g. invalid title)

```json
{ "error": "bad request", "message": "title must not be blank" }
```

### `500` — LLM / internal failure on `/api/chat`

Returned as a `StructuredChatResponse` with `success: false`, `data` populated with a fallback `textResponse`, and `chatId` echoed when known.

---

## Status enum reference

```
ACTIVE     // chat accepting new messages; default sidebar view
ARCHIVED   // soft-deleted; read-only; can be unarchived
COMPLETED  // internal lifecycle state — chat-write path never sets this
FAILED     // internal lifecycle state
EXPIRED    // internal lifecycle state (TTL not enforced today)
```

In practice the UI only needs `ACTIVE` and `ARCHIVED`.

---

## End-to-end flow

```bash
USER=dev

# 1. Send first message — creates chat
RESP=$(curl -s -X POST :8080/api/chat -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER\",\"message\":\"What happened with order 224143?\"}")
CHAT=$(jq -r .chatId <<< "$RESP")

# 2. List
curl -s ":8080/api/chats?userId=$USER" | jq

# 3. Rename
curl -s -X PATCH ":8080/api/chats/$CHAT?userId=$USER" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Order 224143 investigation"}' | jq

# 4. Follow-up (memory replay)
curl -s -X POST :8080/api/chat -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER\",\"chatId\":\"$CHAT\",\"message\":\"What pick package code?\"}" | jq

# 5. Read messages
curl -s ":8080/api/chats/$CHAT/messages?userId=$USER" | jq

# 6. Archive
curl -s -X POST ":8080/api/chats/$CHAT/archive?userId=$USER" | jq

# 7. Default list now empty
curl -s ":8080/api/chats?userId=$USER" | jq

# 8. ARCHIVED list shows it
curl -s ":8080/api/chats?userId=$USER&status=ARCHIVED" | jq

# 9. POST to archived → 404
curl -i -X POST :8080/api/chat -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER\",\"chatId\":\"$CHAT\",\"message\":\"x\"}"

# 10. Unarchive + post → memory still intact
curl -s -X POST ":8080/api/chats/$CHAT/unarchive?userId=$USER" | jq
curl -s -X POST :8080/api/chat -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER\",\"chatId\":\"$CHAT\",\"message\":\"continue\"}" | jq

# 11. Submit thumbs-up feedback on turn 1
curl -s -X POST ":8080/api/chats/$CHAT/messages/1/feedback?userId=$USER" \
  -H 'Content-Type: application/json' \
  -d '{"helpful":true,"comment":"clean answer"}' | jq

# 12. Re-submit (upsert) — flip to thumbs-down
curl -s -X POST ":8080/api/chats/$CHAT/messages/1/feedback?userId=$USER" \
  -H 'Content-Type: application/json' \
  -d '{"helpful":false,"comment":"actually missing the WCS phantom-close angle"}' | jq

# 13. Read messages — feedback + LLM grade visible
curl -s ":8080/api/chats/$CHAT/messages?userId=$USER" | jq '.[] | {seq: .sequence, answered, helpful, feedbackComment}'
```

## Gap-review query

The actual product use case for both LLM grading and user feedback. Run this against the DB to surface coverage gaps:

```sql
SELECT s.user_id, c.session_id AS chat_id, c.sequence_number, c.query,
       c.answered, c.unanswered_reason,
       c.helpful, c.feedback_comment,
       c.created_at
FROM conversation c
JOIN session s ON s.id = c.session_id
WHERE c.answered = false
   OR c.helpful = false
   OR c.response LIKE 'ERROR:%'
ORDER BY c.created_at DESC;
```

Three signal sources, all on the same row:
- `answered = false` — LLM said it couldn't answer.
- `helpful = false` — user gave a thumbs-down.
- `response LIKE 'ERROR:%'` — exception during the turn.

A row matching any of these is a gap worth investigating.
