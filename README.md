# OpsVision WMS Investigation Harness

A Spring Boot application that orchestrates warehouse management system (WMS) investigations by calling MCP (Model Context Protocol) server tools, managing conversation context, and generating AI-powered analysis via OpenAI.

## Architecture overview

- **API layer**: REST endpoints for chat-driven investigations.
- **Chat service**: `SimpleChatService` runs a single LLM-driven turn — extract intent, ask the LLM which discovered tools to call, execute them, then ask the LLM to summarize.
- **MCP client**: `DynamicMcpService` discovers tools at runtime from the configured MCP server and caches them for 5 minutes.
- **AI**: Spring AI's OpenAI ChatClient.
- **Persistence**: PostgreSQL for sessions/conversations/tool executions, Redis for caching.

## Tech stack

- Java 21, Spring Boot 4
- Spring AI (`2.0.0-M4` milestone) — OpenAI starter + MCP client starter
- Spring Data JPA + PostgreSQL, Spring Data Redis
- Flyway for migrations
- Resilience4j (configured but not yet wired into MCP path)

## Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL 15+ (native or via the included `docker-compose.yml`)
- Redis 7+ (native or via Docker)
- An MCP server reachable at `http://localhost:8081/mcp` (the URL is configured in `application.yml` under `spring.ai.mcp.client.streamable-http.connections.opsvision-mcp-server.url`)
- An OpenAI API key

## Quick start

### 1. Start dependencies

Either via Docker:

```bash
docker-compose up -d postgres redis
```

Or use a native PostgreSQL/Redis. If native, create the role + database:

```sql
CREATE ROLE opsvision WITH LOGIN PASSWORD 'opsvision';
CREATE DATABASE opsvision OWNER opsvision;
```

### 2. Set environment variables

```bash
export OPENAI_API_KEY=sk-...
export DB_USERNAME=opsvision
export DB_PASSWORD=opsvision
```

`OPENAI_API_KEY` has no fallback — the app will refuse to start if it's unset.

### 3. Start the MCP server

The harness is a *client*. Bring up the MCP server it talks to (out of scope for this repo) on `http://localhost:8081/mcp`.

### 4. Run the application

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080`. Flyway runs migrations on startup.

### 5. Verify

```bash
curl http://localhost:8080/api/v1/health
```

You should see `database: UP`, `redis: UP`, and `mcp_tools: UP - N tools available`.

## API

### Chat (main endpoint)

```http
POST /api/chat
Content-Type: application/json

{
  "userId": "jim",
  "message": "What happened with order ORD-12345?"
}
```

Response:

```json
{
  "response": "AI-generated analysis…",
  "success": true,
  "error": null,
  "toolsUsed": ["get_order_timeline", "get_inventory_history"]
}
```

### Sample queries

```http
GET /api/chat/samples
```

Returns example prompts for common investigation patterns.

### Discovered tools

```http
GET /api/chat/tools
POST /api/chat/tools/refresh
```

`GET /tools` returns the tools `DynamicMcpService` has discovered from the MCP server (cached 5 min). `POST /tools/refresh` invalidates the cache and re-discovers.

### Smoke test (raw OpenAI)

```http
POST /api/chat/test
Content-Type: application/json

{ "userId": "jim", "message": "say hello" }
```

Bypasses MCP. Useful to confirm the `OPENAI_API_KEY` and ChatClient wiring are healthy without involving the MCP server.

### Health

```http
GET /api/v1/health        # full health: db, redis, mcp_tools, ai
GET /api/v1/health/ready  # readiness
GET /api/v1/health/live   # liveness
```

## Configuration

Key entries in `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/opsvision
    username: ${DB_USERNAME:opsvision}
    password: ${DB_PASSWORD:opsvision}

  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-4.1-nano}
          temperature: ${OPENAI_TEMPERATURE:0.1}
          max-tokens: ${OPENAI_MAX_TOKENS:2000}

    mcp:
      client:
        streamable-http:
          connections:
            opsvision-mcp-server:
              url: "http://localhost:8081/mcp"
```

To point at a different MCP server, edit the `url:` line.

## MCP integration

Tools are discovered dynamically from the MCP server at runtime via `DynamicMcpService.discoverAvailableTools()`. There is no hardcoded tool list — whatever the server advertises through `tools/list` is what the LLM sees. The discovered set is cached for 5 minutes and can be force-refreshed via `POST /api/chat/tools/refresh`.

## Database schema

Three core tables (created by `V1__Initial_schema.sql`):

- `session` — investigation sessions
- `conversation` — individual queries/responses within a session
- `tool_execution` — records of MCP tool invocations

Plus `session_metadata` for key/value session-level metadata.

## Tests

Tests were removed during the simplification refactor (see commit `e91d5c0`) and have not been re-added. `mvn test` is currently a no-op.

## Notes

- Flyway runs on startup against the configured datasource. If you've manually applied migrations earlier, expect a baseline conflict — drop and recreate the database for a clean run.
- CORS on `/api/chat` is wide open (`origins = "*"`). Restrict before any non-local deployment.
- Spring AI is on milestone `2.0.0-M4`. Pin a GA version once available.
