# 🚀 API Testing Commands

## Quick Test Suite - Copy & Paste Ready

### 1. Start a New Chat Conversation
```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What happened with order 12345?",
    "userId": "demo-user"
  }'
```

### 2. Get Sample Queries
```bash
curl http://localhost:8080/api/v1/chat/samples
```

### 3. System Health Check
```bash
curl http://localhost:8080/api/v1/chat/health
```

### 4. Application Health 
```bash
curl http://localhost:8080/actuator/health
```

### 5. Demo Information
```bash
curl http://localhost:8080/api/v1/chat/demo
```

### 6. Start Investigation (Legacy)
```bash
curl -X POST http://localhost:8080/api/v1/investigations \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Investigate order ABC123 delays",
    "userId": "test-user"
  }'
```

### 7. Continue Conversation (Replace sessionId with actual ID from response)
```bash
curl -X POST http://localhost:8080/api/v1/chat/REPLACE_WITH_ACTUAL_SESSION_ID \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What can we do to fix this?",
    "userId": "demo-user"
  }'
```

### 8. Get Conversation History (Replace sessionId)
```bash
curl "http://localhost:8080/api/v1/chat/REPLACE_WITH_ACTUAL_SESSION_ID/history?limit=5"
```

### 9. Get User Sessions
```bash
curl "http://localhost:8080/api/v1/chat/sessions?userId=demo-user&limit=5"
```

## Advanced Testing with Different Messages

### Order Investigation Scenarios
```bash
# Scenario 1: Order Timeline
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Show me the timeline for order XYZ789", "userId": "demo-user"}'

# Scenario 2: Inventory Check  
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Check inventory for order 54321", "userId": "demo-user"}'

# Scenario 3: Shipping Status
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is the shipping status for order DEF456?", "userId": "demo-user"}'

# Scenario 4: Issue Investigation
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Are there any issues with order GHI789?", "userId": "demo-user"}'
```

## JSON Response Examples

### Successful Chat Response
```json
{
  "sessionId": "123e4567-e89b-12d3-a456-426614174000",
  "response": "I found several issues with order 12345. The item wasn't found at expected location...",
  "success": true,
  "toolsUsed": ["get_order_timeline", "get_task_history"],
  "responseTime": "1.2s",
  "timestamp": "2026-05-05T14:54:00.000Z"
}
```

### Error Response Example
```json
{
  "sessionId": null,
  "response": "I apologize, but something unexpected happened. I'm here to help with warehouse operations and investigations.",
  "success": false,
  "errorMessage": "Internal server error",
  "timestamp": "2026-05-05T14:54:00.000Z"
}
```

## Request Validation Rules

### ChatRequest
- `message`: Required, cannot be blank
- `userId`: Required, cannot be null
- `sessionId`: Optional (for continuing conversations)
- `context`: Optional configuration object

### InvestigationRequest  
- `query`: Required, 3-1000 characters
- `userId`: Required, cannot be blank
- `metadata`: Optional key-value pairs

## Testing Tips

1. **Start with Health Checks**: Always test `/actuator/health` first
2. **Use Sample Queries**: Get ideas from `/api/v1/chat/samples`
3. **Save Session IDs**: Copy sessionId from responses for continuation calls
4. **Test Error Cases**: Try invalid requests to see error handling
5. **Monitor Logs**: Check application logs for detailed debugging info

## Postman Collection
Import this URL in Postman for a complete collection:
- Base URL: `http://localhost:8080`
- Add `/api/v1/chat` and `/api/v1/investigations` folders
- Set `userId` as a collection variable: `demo-user`