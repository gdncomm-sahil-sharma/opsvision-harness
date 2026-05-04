package com.opsvision.harness.service.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.harness.model.dto.ToolResult;
import com.opsvision.harness.model.enums.ToolExecutionStatus;
import com.opsvision.harness.model.enums.ToolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class InternalToolService {
    
    private static final Logger log = LoggerFactory.getLogger(InternalToolService.class);
    
    @Autowired
    private ObjectMapper objectMapper;
    
    public ToolResult getOrderTimeline(String orderId) {
        log.info("Executing internal get_order_timeline for order: {}", orderId);
        
        Map<String, Object> parameters = Map.of("order_id", orderId);
        ToolResult result = new ToolResult(ToolType.GET_ORDER_TIMELINE, parameters);
        
        try {
            // Simulate realistic order timeline data
            Map<String, Object> timelineData = createOrderTimelineData(orderId);
            JsonNode jsonResult = objectMapper.valueToTree(timelineData);
            
            result.setResult(jsonResult);
            result.setStatus(ToolExecutionStatus.SUCCESS);
            result.setExecutionTimeMs(150); // Realistic execution time
            
            log.info("Successfully generated order timeline for order: {}", orderId);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to generate order timeline for order {}: {}", orderId, e.getMessage());
            result.setStatus(ToolExecutionStatus.FAILED);
            result.setErrorMessage("Internal tool error: " + e.getMessage());
            return result;
        }
    }
    
    public ToolResult getTaskHistory(String orderId) {
        log.info("Executing internal get_task_history for order: {}", orderId);
        
        Map<String, Object> parameters = Map.of("order_id", orderId);
        ToolResult result = new ToolResult(ToolType.GET_TASK_HISTORY, parameters);
        
        try {
            Map<String, Object> taskData = createTaskHistoryData(orderId);
            JsonNode jsonResult = objectMapper.valueToTree(taskData);
            
            result.setResult(jsonResult);
            result.setStatus(ToolExecutionStatus.SUCCESS);
            result.setExecutionTimeMs(200);
            
            log.info("Successfully generated task history for order: {}", orderId);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to generate task history for order {}: {}", orderId, e.getMessage());
            result.setStatus(ToolExecutionStatus.FAILED);
            result.setErrorMessage("Internal tool error: " + e.getMessage());
            return result;
        }
    }
    
    public ToolResult getInventoryHistory(String orderId) {
        log.info("Executing internal get_inventory_history for order: {}", orderId);
        
        Map<String, Object> parameters = Map.of("order_id", orderId);
        ToolResult result = new ToolResult(ToolType.GET_INVENTORY_HISTORY, parameters);
        
        try {
            Map<String, Object> inventoryData = createInventoryHistoryData(orderId);
            JsonNode jsonResult = objectMapper.valueToTree(inventoryData);
            
            result.setResult(jsonResult);
            result.setStatus(ToolExecutionStatus.SUCCESS);
            result.setExecutionTimeMs(180);
            
            log.info("Successfully generated inventory history for order: {}", orderId);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to generate inventory history for order {}: {}", orderId, e.getMessage());
            result.setStatus(ToolExecutionStatus.FAILED);
            result.setErrorMessage("Internal tool error: " + e.getMessage());
            return result;
        }
    }
    
    public ToolResult getAuditEvents(String orderId) {
        log.info("Executing internal get_audit_events for order: {}", orderId);
        
        Map<String, Object> parameters = Map.of("order_id", orderId);
        ToolResult result = new ToolResult(ToolType.GET_AUDIT_EVENTS, parameters);
        
        try {
            Map<String, Object> auditData = createAuditEventsData(orderId);
            JsonNode jsonResult = objectMapper.valueToTree(auditData);
            
            result.setResult(jsonResult);
            result.setStatus(ToolExecutionStatus.SUCCESS);
            result.setExecutionTimeMs(120);
            
            log.info("Successfully generated audit events for order: {}", orderId);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to generate audit events for order {}: {}", orderId, e.getMessage());
            result.setStatus(ToolExecutionStatus.FAILED);
            result.setErrorMessage("Internal tool error: " + e.getMessage());
            return result;
        }
    }
    
    private Map<String, Object> createOrderTimelineData(String orderId) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        // Create realistic order timeline based on order ID
        boolean hasIssues = orderId.equals("12345"); // This order has shipping issues
        
        List<Map<String, Object>> timeline = new ArrayList<>();
        
        // Order received
        timeline.add(Map.of(
            "timestamp", now.minusDays(3).format(formatter),
            "status", "ORDER_RECEIVED",
            "description", "Order received from customer",
            "location", "ORDER_PROCESSING",
            "operator", "SYSTEM"
        ));
        
        // Payment processed
        timeline.add(Map.of(
            "timestamp", now.minusDays(3).plusHours(1).format(formatter),
            "status", "PAYMENT_PROCESSED",
            "description", "Payment verified and processed",
            "location", "FINANCE",
            "operator", "AUTO_PROCESSOR"
        ));
        
        // Inventory allocated
        timeline.add(Map.of(
            "timestamp", now.minusDays(2).format(formatter),
            "status", "INVENTORY_ALLOCATED",
            "description", "Items allocated from warehouse inventory",
            "location", "WAREHOUSE_A",
            "operator", "INV_SYSTEM"
        ));
        
        if (hasIssues) {
            // Picking failed
            timeline.add(Map.of(
                "timestamp", now.minusDays(1).format(formatter),
                "status", "PICKING_FAILED",
                "description", "Item not found at expected location - SKU-ABC-123 missing from bin A-15-C",
                "location", "WAREHOUSE_A",
                "operator", "PICKER_007",
                "issue_code", "ITEM_NOT_FOUND"
            ));
            
            // Inventory recount
            timeline.add(Map.of(
                "timestamp", now.minusDays(1).plusHours(2).format(formatter),
                "status", "INVENTORY_RECOUNT_INITIATED",
                "description", "Cycle count initiated for location A-15-C",
                "location", "WAREHOUSE_A",
                "operator", "SUPERVISOR_12"
            ));
            
            // Still pending
            timeline.add(Map.of(
                "timestamp", now.minusHours(4).format(formatter),
                "status", "PENDING_INVENTORY_RESOLUTION",
                "description", "Order on hold pending inventory count resolution",
                "location", "WAREHOUSE_A", 
                "operator", "SYSTEM"
            ));
        } else {
            // Normal flow - picked and shipped
            timeline.add(Map.of(
                "timestamp", now.minusDays(1).format(formatter),
                "status", "PICKED",
                "description", "All items successfully picked",
                "location", "WAREHOUSE_A",
                "operator", "PICKER_005"
            ));
            
            timeline.add(Map.of(
                "timestamp", now.minusDays(1).plusHours(2).format(formatter),
                "status", "PACKED",
                "description", "Order packed for shipping",
                "location", "PACKING_STATION_3",
                "operator", "PACKER_022"
            ));
            
            timeline.add(Map.of(
                "timestamp", now.minusDays(1).plusHours(4).format(formatter),
                "status", "SHIPPED",
                "description", "Order dispatched via UPS - Tracking: 1Z999AA1234567890",
                "location", "SHIPPING_DOCK_B",
                "operator", "SHIPPER_08"
            ));
        }
        
        return Map.of(
            "order_id", orderId,
            "timeline", timeline,
            "current_status", hasIssues ? "PENDING_INVENTORY_RESOLUTION" : "SHIPPED",
            "total_events", timeline.size(),
            "last_updated", now.format(formatter)
        );
    }
    
    private Map<String, Object> createTaskHistoryData(String orderId) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        boolean hasIssues = orderId.equals("12345");
        
        List<Map<String, Object>> tasks = new ArrayList<>();
        
        tasks.add(Map.of(
            "task_id", "TSK-" + orderId + "-001",
            "type", "ALLOCATION",
            "status", "COMPLETED",
            "assigned_to", "INV_SYSTEM",
            "created_at", now.minusDays(2).format(formatter),
            "completed_at", now.minusDays(2).plusMinutes(5).format(formatter),
            "description", "Allocate inventory for order items",
            "location", "WAREHOUSE_A"
        ));
        
        tasks.add(Map.of(
            "task_id", "TSK-" + orderId + "-002", 
            "type", "PICKING",
            "status", hasIssues ? "FAILED" : "COMPLETED",
            "assigned_to", hasIssues ? "PICKER_007" : "PICKER_005",
            "created_at", now.minusDays(1).format(formatter),
            "completed_at", hasIssues ? null : now.minusDays(1).plusHours(1).format(formatter),
            "description", hasIssues ? "Pick items - FAILED: Item SKU-ABC-123 not found at location A-15-C" : "Pick all order items from warehouse locations",
            "location", "WAREHOUSE_A",
            "failure_reason", hasIssues ? "ITEM_NOT_FOUND_AT_LOCATION" : null
        ));
        
        if (hasIssues) {
            tasks.add(Map.of(
                "task_id", "TSK-" + orderId + "-003",
                "type", "CYCLE_COUNT",
                "status", "IN_PROGRESS", 
                "assigned_to", "COUNTER_15",
                "created_at", now.minusDays(1).plusHours(2).format(formatter),
                "description", "Perform cycle count for location A-15-C to resolve inventory discrepancy",
                "location", "WAREHOUSE_A_BIN_A15C",
                "priority", "HIGH"
            ));
        } else {
            tasks.add(Map.of(
                "task_id", "TSK-" + orderId + "-003",
                "type", "PACKING",
                "status", "COMPLETED",
                "assigned_to", "PACKER_022",
                "created_at", now.minusDays(1).plusHours(1).format(formatter),
                "completed_at", now.minusDays(1).plusHours(3).format(formatter),
                "description", "Pack items for shipping",
                "location", "PACKING_STATION_3"
            ));
        }
        
        return Map.of(
            "order_id", orderId,
            "tasks", tasks,
            "total_tasks", tasks.size(),
            "completed_tasks", (int) tasks.stream().mapToLong(t -> "COMPLETED".equals(t.get("status")) ? 1 : 0).sum(),
            "failed_tasks", (int) tasks.stream().mapToLong(t -> "FAILED".equals(t.get("status")) ? 1 : 0).sum()
        );
    }
    
    private Map<String, Object> createInventoryHistoryData(String orderId) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        boolean hasIssues = orderId.equals("12345");
        
        List<Map<String, Object>> movements = new ArrayList<>();
        
        movements.add(Map.of(
            "timestamp", now.minusDays(2).format(formatter),
            "sku", "SKU-ABC-123",
            "description", "Wireless Bluetooth Headphones",
            "movement_type", "ALLOCATION",
            "quantity", -2,
            "location_from", "AVAILABLE",
            "location_to", "ALLOCATED_" + orderId,
            "operator", "SYSTEM",
            "reference", "ORDER_" + orderId
        ));
        
        movements.add(Map.of(
            "timestamp", now.minusDays(2).format(formatter),
            "sku", "SKU-DEF-456", 
            "description", "USB Charging Cable",
            "movement_type", "ALLOCATION",
            "quantity", -1,
            "location_from", "AVAILABLE",
            "location_to", "ALLOCATED_" + orderId,
            "operator", "SYSTEM",
            "reference", "ORDER_" + orderId
        ));
        
        if (hasIssues) {
            Map<String, Object> pickFailure = new HashMap<>();
            pickFailure.put("timestamp", now.minusDays(1).format(formatter));
            pickFailure.put("sku", "SKU-ABC-123");
            pickFailure.put("description", "Wireless Bluetooth Headphones");
            pickFailure.put("movement_type", "PICK_FAILURE");
            pickFailure.put("quantity", 0);
            pickFailure.put("location_from", "A-15-C");
            pickFailure.put("location_to", "ALLOCATED_" + orderId);
            pickFailure.put("operator", "PICKER_007");
            pickFailure.put("reference", "PICK_TASK_" + orderId);
            pickFailure.put("issue", "Physical inventory not found at expected location A-15-C. System shows 5 units, physical count shows 3 units.");
            pickFailure.put("discrepancy", -2);
            movements.add(pickFailure);
        } else {
            movements.add(Map.of(
                "timestamp", now.minusDays(1).format(formatter),
                "sku", "SKU-ABC-123",
                "description", "Wireless Bluetooth Headphones",
                "movement_type", "PICK",
                "quantity", -2,
                "location_from", "A-15-C", 
                "location_to", "PICKED_" + orderId,
                "operator", "PICKER_005",
                "reference", "PICK_TASK_" + orderId
            ));
            
            movements.add(Map.of(
                "timestamp", now.minusDays(1).format(formatter),
                "sku", "SKU-DEF-456",
                "description", "USB Charging Cable",
                "movement_type", "PICK",
                "quantity", -1,
                "location_from", "B-08-A",
                "location_to", "PICKED_" + orderId, 
                "operator", "PICKER_005",
                "reference", "PICK_TASK_" + orderId
            ));
        }
        
        return Map.of(
            "order_id", orderId,
            "inventory_movements", movements,
            "total_movements", movements.size(),
            "items_allocated", 2,
            "items_picked", hasIssues ? 1 : 2,
            "inventory_issues", hasIssues ? List.of(
                Map.of(
                    "sku", "SKU-ABC-123",
                    "issue", "Inventory discrepancy at location A-15-C",
                    "expected_qty", 5,
                    "actual_qty", 3,
                    "variance", -2
                )
            ) : List.of()
        );
    }
    
    private Map<String, Object> createAuditEventsData(String orderId) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        boolean hasIssues = orderId.equals("12345");
        
        List<Map<String, Object>> events = new ArrayList<>();
        
        events.add(Map.of(
            "event_id", "AUD-" + System.currentTimeMillis() + "-001",
            "timestamp", now.minusDays(3).format(formatter),
            "event_type", "ORDER_CREATED",
            "user", "CUSTOMER_PORTAL",
            "description", "Order created by customer",
            "metadata", Map.of(
                "order_id", orderId,
                "customer_id", "CUST_789",
                "order_total", 299.99,
                "payment_method", "CREDIT_CARD"
            )
        ));
        
        events.add(Map.of(
            "event_id", "AUD-" + System.currentTimeMillis() + "-002",
            "timestamp", now.minusDays(2).format(formatter),
            "event_type", "INVENTORY_ALLOCATED",
            "user", "INVENTORY_SYSTEM",
            "description", "Inventory allocated for order fulfillment",
            "metadata", Map.of(
                "order_id", orderId,
                "allocated_items", 2,
                "warehouse", "WAREHOUSE_A"
            )
        ));
        
        if (hasIssues) {
            events.add(Map.of(
                "event_id", "AUD-" + System.currentTimeMillis() + "-003",
                "timestamp", now.minusDays(1).format(formatter),
                "event_type", "PICKING_EXCEPTION",
                "user", "PICKER_007",
                "severity", "HIGH",
                "description", "Picking task failed - inventory not found at expected location",
                "metadata", Map.of(
                    "order_id", orderId,
                    "sku", "SKU-ABC-123",
                    "expected_location", "A-15-C",
                    "expected_qty", 5,
                    "found_qty", 3,
                    "exception_code", "INV_LOCATION_MISMATCH"
                )
            ));
            
            events.add(Map.of(
                "event_id", "AUD-" + System.currentTimeMillis() + "-004",
                "timestamp", now.minusDays(1).plusHours(1).format(formatter),
                "event_type", "SUPERVISOR_ESCALATION",
                "user", "SUPERVISOR_12",
                "description", "Inventory discrepancy escalated for resolution",
                "metadata", Map.of(
                    "order_id", orderId,
                    "escalation_reason", "INVENTORY_VARIANCE",
                    "action_taken", "CYCLE_COUNT_INITIATED"
                )
            ));
            
            events.add(Map.of(
                "event_id", "AUD-" + System.currentTimeMillis() + "-005",
                "timestamp", now.minusHours(8).format(formatter),
                "event_type", "ORDER_HOLD",
                "user", "WMS_SYSTEM",
                "description", "Order placed on hold pending inventory count resolution",
                "metadata", Map.of(
                    "order_id", orderId,
                    "hold_reason", "PENDING_CYCLE_COUNT",
                    "estimated_resolution", now.plusHours(4).format(formatter)
                )
            ));
        } else {
            events.add(Map.of(
                "event_id", "AUD-" + System.currentTimeMillis() + "-003",
                "timestamp", now.minusDays(1).format(formatter),
                "event_type", "ORDER_PICKED",
                "user", "PICKER_005",
                "description", "All items successfully picked",
                "metadata", Map.of(
                    "order_id", orderId,
                    "pick_time_minutes", 45,
                    "items_picked", 2
                )
            ));
            
            events.add(Map.of(
                "event_id", "AUD-" + System.currentTimeMillis() + "-004",
                "timestamp", now.minusDays(1).plusHours(4).format(formatter),
                "event_type", "ORDER_SHIPPED",
                "user", "SHIPPING_SYSTEM",
                "description", "Order shipped to customer",
                "metadata", Map.of(
                    "order_id", orderId,
                    "tracking_number", "1Z999AA1234567890",
                    "carrier", "UPS",
                    "service_level", "GROUND"
                )
            ));
        }
        
        return Map.of(
            "order_id", orderId,
            "audit_events", events,
            "total_events", events.size(),
            "critical_events", hasIssues ? 2 : 0,
            "last_event", events.get(events.size() - 1).get("timestamp")
        );
    }
}