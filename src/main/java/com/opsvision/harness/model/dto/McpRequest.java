package com.opsvision.harness.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class McpRequest {
    
    private String method;
    private McpParams params;
    
    public McpRequest() {}
    
    public McpRequest(String method, McpParams params) {
        this.method = method;
        this.params = params;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public McpParams getParams() {
        return params;
    }

    public void setParams(McpParams params) {
        this.params = params;
    }

    public static class McpParams {
        private String name;
        private Map<String, Object> arguments;

        public McpParams() {}

        public McpParams(String name, Map<String, Object> arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }

        public void setArguments(Map<String, Object> arguments) {
            this.arguments = arguments;
        }
    }
}