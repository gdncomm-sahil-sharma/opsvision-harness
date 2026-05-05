# API Test Commands - Dynamic Tool Discovery

This document contains curl commands to test the simplified chat API with dynamic MCP tool discovery.

## 🔧 Core Chat Endpoints

### 1. Order Investigation
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "ops-user", "message": "What happened with order 223506?"}'
```

### 2. Pick Package Analysis
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "warehouse-user", "message": "Analyze pick package PP-12345 and check if it's stuck"}'
```

### 3. SKU Inventory Check
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "inventory-user", "message": "Check inventory for SKU ABC-123 at warehouse_east"}'
```

### 4. Task Investigation
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "task-user", "message": "Find all stuck picking tasks from today"}'
```

### 5. Stock Trace Analysis
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "trace-user", "message": "Trace stock movements for UUID f47ac10b-58cc-4372-a567-0e02b2c3d479"}'
```

### 6. Pick List Evaluation
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "eval-user", "message": "Evaluate pick list readiness for PL-98765"}'
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

### 8. MCP Session Health Check
```bash
curl -X GET http://localhost:8080/api/chat/session/health | jq
```

## 🎯 Key Features Tested

- **Dynamic Tool Discovery**: Tools discovered from MCP server at runtime
- **Intelligent Parameter Extraction**: Automatically detects IDs, SKUs, UUIDs, filters from user input
- **Smart Tool Selection**: LLM matches user requests to appropriate warehouse investigation tools
- **Multi-Parameter Support**: Handles order IDs, pick package IDs, pick list IDs, stock trace UUIDs, SKUs, sites, etc.
- **Contextual Analysis**: Tools selected based on request type (inventory, tasks, tracing, evaluation)
- **Real Data Analysis**: Actual MCP tool execution with extracted parameters
- **Flexible Architecture**: Adapts to any MCP tools without code changes

## Expected Response Format

```json
{
  "response": "Detailed analysis based on real warehouse investigation data with specific findings, identifiers, and actionable recommendations...",
  "success": true,
  "error": null,
  "toolsUsed": ["functions.getPickPackage", "functions.evaluatePickListReadiness"]
}
```

## Parameter Extraction Examples

The system intelligently extracts parameters from natural language:

- **"order 223506"** → `orderId: "223506"`
- **"pick package PP-12345"** → `pickPackageId: "PP-12345"`  
- **"SKU ABC-123 at warehouse_east"** → `sku: "ABC-123"`, `site: "warehouse_east"`
- **"stuck tasks from today"** → `status: "stuck"`, `timeFilter: "today"`
- **"UUID f47ac10b-58cc-4372-a567-0e02b2c3d479"** → `stockTraceId: "f47ac10b-58cc..."`

## 📝 Notes

- **Dynamic Discovery**: All tools discovered from MCP server using proper MCP protocol
- **Zero Hardcoding**: No tool names, parameters, or IDs hardcoded anywhere
- **Intelligent Parsing**: System extracts order IDs, pick package IDs, SKUs, UUIDs, filters, etc. from natural language
- **Contextual Tool Selection**: LLM matches requests to appropriate warehouse investigation tools
- **Parameter Flexibility**: Works with any parameter types (IDs, filters, dates, quantities, priorities)
- **Adaptive Architecture**: System automatically adapts when MCP server tools change
- **Session Resilience**: Built-in retry logic and session recovery for reliable tool execution