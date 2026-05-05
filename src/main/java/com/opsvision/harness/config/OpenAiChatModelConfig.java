package com.opsvision.harness.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for OpenAI ChatModel
 */
@Configuration
@ConfigurationProperties(prefix = "spring.ai.openai")
public class OpenAiChatModelConfig {
    
    private static final Logger log = LoggerFactory.getLogger(OpenAiChatModelConfig.class);
    
    private String apiKey;
    private Chat chat = new Chat();
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public Chat getChat() {
        return chat;
    }
    
    public void setChat(Chat chat) {
        this.chat = chat;
    }
    
    /**
     * OpenAI Chat-specific configuration
     */
    public static class Chat {
        private Options options = new Options();
        
        public Options getOptions() {
            return options;
        }
        
        public void setOptions(Options options) {
            this.options = options;
        }
    }
    
    /**
     * OpenAI Chat options configuration
     */
    public static class Options {
        private String model;
        private Float temperature;
        private Integer maxTokens;
        private Float topP;
        private Integer topK;
        private String[] stop;
        private Integer presencePenalty;
        private Integer frequencyPenalty;
        
        public String getModel() {
            return model;
        }
        
        public void setModel(String model) {
            this.model = model;
        }
        
        public Float getTemperature() {
            return temperature;
        }
        
        public void setTemperature(Float temperature) {
            this.temperature = temperature;
        }
        
        public Integer getMaxTokens() {
            return maxTokens;
        }
        
        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }
        
        public Float getTopP() {
            return topP;
        }
        
        public void setTopP(Float topP) {
            this.topP = topP;
        }
        
        public Integer getTopK() {
            return topK;
        }
        
        public void setTopK(Integer topK) {
            this.topK = topK;
        }
        
        public String[] getStop() {
            return stop;
        }
        
        public void setStop(String[] stop) {
            this.stop = stop;
        }
        
        public Integer getPresencePenalty() {
            return presencePenalty;
        }
        
        public void setPresencePenalty(Integer presencePenalty) {
            this.presencePenalty = presencePenalty;
        }
        
        public Integer getFrequencyPenalty() {
            return frequencyPenalty;
        }
        
        public void setFrequencyPenalty(Integer frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
        }
    }
    
    /**
     * Validate the configuration
     */
    public boolean isValid() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("OpenAI API key is not configured");
            return false;
        }
        
        if (chat.options.model == null || chat.options.model.trim().isEmpty()) {
            log.warn("OpenAI model is not configured");
            return false;
        }
        
        return true;
    }
    
    /**
     * Log the current configuration (without exposing API key)
     */
    public void logConfiguration() {
        log.info("OpenAI Configuration:");
        log.info("  API Key: {} characters long", apiKey != null ? apiKey.length() : 0);
        log.info("  Model: {}", chat.options.model);
        log.info("  Temperature: {}", chat.options.temperature);
        log.info("  Max Tokens: {}", chat.options.maxTokens);
        log.info("  Top P: {}", chat.options.topP);
        log.info("  Top K: {}", chat.options.topK);
    }
}