package com.opsvision.harness.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.modelcontextprotocol.client.McpSyncClient;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Test service to explore ToolCallback methods
 */
@Service
public class ToolCallbackTestService {
    
    private static final Logger log = LoggerFactory.getLogger(ToolCallbackTestService.class);
    
    @Autowired(required = false)
    private McpSyncClient mcpClient;
    
    public void exploreToolCallbackMethods() {
        try {
            if (mcpClient != null) {
                log.info("=== COMPLETE TOOLCALLBACK OBJECT EXPLORATION ===");
                
                var provider = new SyncMcpToolCallbackProvider(mcpClient);
                ToolCallback[] toolCallbacks = provider.getToolCallbacks();
                
                if (toolCallbacks != null && toolCallbacks.length > 0) {
                    ToolCallback callback = toolCallbacks[0];
                    
                    // 1. Print complete object toString()
                    log.info("COMPLETE ToolCallback object toString(): {}", callback.toString());
                    
                    // 2. Print class name and hierarchy
                    log.info("ToolCallback class: {}", callback.getClass().getName());
                    log.info("ToolCallback superclass: {}", callback.getClass().getSuperclass());
                    log.info("ToolCallback interfaces: {}", Arrays.toString(callback.getClass().getInterfaces()));
                    
                    // 3. Log all available methods with return types
                    Method[] methods = callback.getClass().getMethods();
                    log.info("Available methods on ToolCallback ({} total):", methods.length);
                    Arrays.stream(methods)
                        .filter(method -> !method.getDeclaringClass().equals(Object.class))
                        .forEach(method -> {
                            try {
                                String paramTypes = Arrays.stream(method.getParameterTypes())
                                    .map(Class::getSimpleName)
                                    .reduce("", (a, b) -> a + (a.isEmpty() ? "" : ", ") + b);
                                log.info("  - {} {}({}) - from {}", 
                                    method.getReturnType().getSimpleName(),
                                    method.getName(), 
                                    paramTypes,
                                    method.getDeclaringClass().getSimpleName());
                            } catch (Exception e) {
                                log.info("  - {} - error getting details: {}", method.getName(), e.getMessage());
                            }
                        });
                    
                    // 4. Try all no-parameter methods that might return useful info
                    String[] methodsToTry = {"getName", "name", "getToolName", "toolName", "getDisplayName", "getId", "toString"};
                    for (String methodName : methodsToTry) {
                        try {
                            Method method = callback.getClass().getMethod(methodName);
                            Object result = method.invoke(callback);
                            log.info("METHOD RESULT: {}() = {}", methodName, result);
                        } catch (NoSuchMethodException e) {
                            log.debug("Method {} not found", methodName);
                        } catch (Exception e) {
                            log.warn("Error calling {}: {}", methodName, e.getMessage());
                        }
                    }
                    
                    // 5. Examine getToolDefinition in detail
                    try {
                        Object toolDef = callback.getToolDefinition();
                        log.info("=== TOOL DEFINITION OBJECT ===");
                        log.info("ToolDefinition class: {}", toolDef.getClass().getName());
                        log.info("ToolDefinition toString(): {}", toolDef);
                        
                        // Try methods on the tool definition object
                        Method[] defMethods = toolDef.getClass().getMethods();
                        log.info("ToolDefinition methods:");
                        Arrays.stream(defMethods)
                            .filter(method -> !method.getDeclaringClass().equals(Object.class))
                            .filter(method -> method.getParameterCount() == 0)
                            .forEach(method -> {
                                try {
                                    Object result = method.invoke(toolDef);
                                    log.info("  - {}() = {}", method.getName(), result);
                                } catch (Exception e) {
                                    log.info("  - {}() - error: {}", method.getName(), e.getMessage());
                                }
                            });
                    } catch (Exception e) {
                        log.error("getToolDefinition() failed: {}", e.getMessage());
                    }
                    
                    // 6. Check a few more callbacks to see if they're consistent
                    if (toolCallbacks.length > 1) {
                        log.info("=== CHECKING OTHER CALLBACKS ===");
                        for (int i = 1; i < Math.min(toolCallbacks.length, 3); i++) {
                            ToolCallback otherCallback = toolCallbacks[i];
                            log.info("Callback {}: {}", i, otherCallback.toString());
                            try {
                                Object otherDef = otherCallback.getToolDefinition();
                                log.info("  Definition: {}", otherDef);
                            } catch (Exception e) {
                                log.warn("  Definition error: {}", e.getMessage());
                            }
                        }
                    }
                }
            } else {
                log.warn("McpSyncClient is null - cannot explore callbacks");
            }
        } catch (Exception e) {
            log.error("Error exploring ToolCallback methods: {}", e.getMessage(), e);
        }
    }
}