# OpsVision WMS Investigation Harness

A production-quality Spring Boot application that orchestrates warehouse management system (WMS) investigations by calling MCP (Model Context Protocol) server tools, managing conversation context, and generating AI-powered root cause analysis.

## 🏗️ Architecture Overview

The system follows a layered architecture with clear separation of concerns:

- **API Layer**: REST endpoints for investigation queries
- **Agent Layer**: Orchestrates investigation workflow and tool selection  
- **MCP Client**: Handles external tool communication with retry logic
- **Context Management**: Session and conversation state management
- **AI Integration**: Spring AI for generating insights
- **Persistence Layer**: Postgres for sessions, Redis for caching

## 🚀 Features

- **Multi-turn Investigation Sessions**: Maintain conversation context across multiple queries
- **Intelligent Tool Selection**: Automatically selects relevant MCP tools based on query analysis
- **AI-Powered Analysis**: Uses Google Gemini (via official Google GenAI SDK) to generate structured root cause analysis
- **Robust Error Handling**: Circuit breakers, retries, and graceful degradation
- **Comprehensive Caching**: Redis caching for performance optimization
- **Production Monitoring**: Health checks, metrics, and structured logging

## 📋 Tech Stack

- **Java 21** with Spring Boot 3
- **Spring AI** for Google Gemini integration
- **Spring Data JPA** with PostgreSQL
- **Spring Data Redis** for caching
- **WebClient** for MCP server communication  
- **Resilience4j** for circuit breakers
- **Flyway** for database migrations

## 🛠️ Prerequisites

- Java 21+
- Maven 3.8+
- Docker and Docker Compose (for databases)
- PostgreSQL 15+
- Redis 7+
- Google GenAI API key

## 🎯 Quick Start

### 1. Start Dependencies

```bash
# Start PostgreSQL and Redis using Docker
docker-compose up -d postgres redis
```

### 2. Set Environment Variables

```bash
export DB_USERNAME=opsvision
export DB_PASSWORD=password
export GOOGLE_GENAI_API_KEY=your_google_genai_api_key
export MCP_BASE_URL=http://localhost:8080
```

> 📋 **Need a Google GenAI API key?** See [SETUP_GEMINI.md](SETUP_GEMINI.md) for detailed instructions.

### 3. Run the Application

```bash
mvn spring-boot:run
```

The application will start on http://localhost:8080

### 4. Verify Health

```bash
curl http://localhost:8080/api/v1/health
```

## 📖 API Documentation

### Start Investigation

```http
POST /api/v1/investigations
Content-Type: application/json

{
  "query": "Order 12345 didn't ship",
  "userId": "user123"
}
```

**Response:**
```json
{
  "sessionId": "uuid",
  "response": "AI-generated investigation analysis...",
  "toolResults": [...],
  "executionTimeMs": 2500,
  "success": true,
  "timestamp": "2026-05-04T17:30:00"
}
```

### Continue Investigation

```http
POST /api/v1/investigations/{sessionId}/continue?query=What can we do to fix this?&userId=user123
```

### Get Session Details

```http
GET /api/v1/investigations/{sessionId}?userId=user123
```

### Get User Sessions

```http
GET /api/v1/investigations/user/{userId}
```

## 🔧 Configuration

Key configuration options in `application.yml`:

```yaml
spring:
  ai:
    model:
      chat: google-genai
    google:
      genai:
        api-key: ${GOOGLE_GENAI_API_KEY}
        chat:
          options:
            model: gemini-2.0-flash-exp
            temperature: 0.1
            max-output-tokens: 2000

mcp:
  base-url: ${MCP_BASE_URL:http://localhost:8080}
  timeout: 30s
  retry:
    max-attempts: 3
    backoff-multiplier: 2

app:
  session:
    max-active-per-user: 5
    default-ttl: 4h
  cache:
    tool-results-ttl: 30m
```

## 🧪 Testing

Run the comprehensive test suite:

```bash
# Run all tests
mvn test

# Run specific test types
mvn test -Dtest="**/*UnitTest"
mvn test -Dtest="**/*IntegrationTest" 
```

The test suite includes:
- Unit tests for service layers
- Repository integration tests  
- REST API integration tests
- End-to-end workflow tests

## 📊 Monitoring & Health Checks

Health check endpoints:

- `GET /api/v1/health` - Overall system health
- `GET /api/v1/health/ready` - Readiness probe
- `GET /api/v1/health/live` - Liveness probe

The health check verifies:
- Database connectivity
- Redis connectivity  
- MCP server availability
- AI service health

## 🗄️ Database Schema

The system uses three main entities:

- **Session**: Investigation sessions with metadata
- **Conversation**: Individual queries and responses within a session
- **ToolExecution**: Records of MCP tool invocations with results

Database migrations are managed with Flyway.

## 🔄 MCP Integration

The system integrates with MCP servers providing these tools:

- `get_order_timeline` - Retrieve order processing timeline
- `get_task_history` - Get warehouse task execution history  
- `get_inventory_history` - Fetch inventory movement data
- `get_audit_events` - Pull audit trail information

Tools are selected intelligently based on query content analysis.

## 🤖 AI Integration

The system uses **Google Gemini** as the AI backend for generating investigation analysis:

### Features
- **Custom Spring AI Integration**: Custom ChatModel implementation using Google GenAI SDK
- **Model Flexibility**: Supports various Gemini models (Flash, Pro, experimental versions)
- **Structured Prompts**: Specialized prompt engineering for WMS investigation analysis
- **Conversation Context**: Maintains conversation history for multi-turn investigations

### Configuration
```yaml
spring:
  ai:
    google:
      genai:
        api-key: ${GOOGLE_GENAI_API_KEY}
        chat:
          options:
            model: gemini-2.0-flash-exp  # Configurable model selection
            temperature: 0.1             # Low temperature for consistent analysis
            max-output-tokens: 2000      # Adequate for detailed responses
```

### API Key Setup
See [SETUP_GEMINI.md](SETUP_GEMINI.md) for detailed Google GenAI API key setup instructions.

## 🚨 Error Handling

The system provides robust error handling:

- **Circuit Breakers**: Fail fast when external services are down
- **Retry Logic**: Exponential backoff for transient failures
- **Graceful Degradation**: Continue with available data when tools fail
- **AI Fallback**: Provides basic responses when AI service is unavailable
- **Structured Errors**: Consistent error response format

## 🔐 Security Considerations

- Input validation on all endpoints
- User-based session isolation
- No sensitive data in logs
- Configurable rate limiting (via load balancer)

## 📈 Performance Features

- Redis caching for session state and tool results
- Parallel tool execution where possible
- Connection pooling for database and HTTP clients
- Lazy loading for entity relationships

## 🚀 Deployment

The application is containerizable and includes:

- Docker support with multi-stage builds
- Health checks for container orchestration  
- Configurable via environment variables
- Graceful shutdown handling

## 🛠️ Development

### Running Tests

```bash
mvn test                    # All tests
mvn test -Dspring.profiles.active=test
```

### Database Migrations

```bash
mvn flyway:migrate         # Run migrations
mvn flyway:info           # Migration status  
```

### IDE Setup

The project uses standard Spring Boot conventions and works with:
- IntelliJ IDEA (recommended)
- Eclipse with Spring Tools
- VS Code with Spring extensions

## 📝 License

This project is proprietary software for OpsVision WMS investigations.

## 🤝 Contributing

1. Follow the existing code style and patterns
2. Add tests for new functionality  
3. Update documentation as needed
4. Ensure all health checks pass
