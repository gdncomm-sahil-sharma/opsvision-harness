package com.opsvision.harness.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class McpResponse {
    
    private McpResult result;
    private McpError error;
    
    public McpResponse() {}

    public McpResult getResult() {
        return result;
    }

    public void setResult(McpResult result) {
        this.result = result;
    }

    public McpError getError() {
        return error;
    }

    public void setError(McpError error) {
        this.error = error;
    }

    public boolean isSuccess() {
        return result != null && error == null;
    }

    public static class McpResult {
        private JsonNode content;
        
        @JsonProperty("isError")
        private boolean isError;

        public McpResult() {}

        public JsonNode getContent() {
            return content;
        }

        public void setContent(JsonNode content) {
            this.content = content;
        }

        public boolean isError() {
            return isError;
        }

        public void setError(boolean error) {
            isError = error;
        }
    }

    public static class McpError {
        private int code;
        private String message;
        private JsonNode data;

        public McpError() {}

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public JsonNode getData() {
            return data;
        }

        public void setData(JsonNode data) {
            this.data = data;
        }
    }
}