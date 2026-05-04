package com.opsvision.harness.model.enums;

public enum ToolType {
    GET_ORDER_TIMELINE("get_order_timeline"),
    GET_TASK_HISTORY("get_task_history"),
    GET_INVENTORY_HISTORY("get_inventory_history"),
    GET_AUDIT_EVENTS("get_audit_events");

    private final String toolName;

    ToolType(String toolName) {
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }

    public static ToolType fromToolName(String toolName) {
        for (ToolType type : values()) {
            if (type.toolName.equals(toolName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown tool name: " + toolName);
    }
}