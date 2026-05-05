# API Test Commands - Dynamic Tool Discovery

This document contains curl commands to test the simplified chat API with dynamic MCP tool discovery.

## 🔧 Core Chat Endpoints

### 1. Main Chat Endpoint (Dynamic Tool Selection)
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "test-user", "message": "What happened with order ORD-12345?"}'
```

### 2. Security Audit Query
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "audit-user", "message": "I need a security audit for order ORD-99999"}'
```

### 3. Inventory Investigation
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "inventory-user", "message": "Check inventory movements for order ORD-33333"}'
```

## 📊 Discovery & Management Endpoints

### 4. Dynamic Tool Discovery
```bash
curl -X GET http://localhost:8080/api/chat/tools | jq
```

### 5. Refresh Tool Cache
```bash
curl -X POST http://localhost:8080/api/chat/tools/refresh | jq
```

### 6. Get Sample Queries
```bash
curl -X GET http://localhost:8080/api/chat/samples | jq
```

### 7. Chat Health Check
```bash
curl -X GET http://localhost:8080/api/chat/health | jq
```

## 🎯 Key Features Tested

- **Dynamic Tool Discovery**: No hardcoded tool names
- **Smart Tool Selection**: LLM chooses appropriate tools based on context
- **Real Data Analysis**: Actual MCP tool execution and result analysis
- **Flexible Tool Support**: Works with any number of MCP tools

## Expected Response Format

```json
{
  "response": "Detailed analysis based on real tool data...",
  "success": true,
  "error": null,
  "toolsUsed": ["dynamic_tool_name_1", "dynamic_tool_name_2"]
}
```

## 📝 Notes

- All tool names are discovered dynamically from MCP server
- No hardcoded tool references anywhere in the system
- LLM intelligently selects tools based on user query context
- System adapts automatically if MCP server tools change