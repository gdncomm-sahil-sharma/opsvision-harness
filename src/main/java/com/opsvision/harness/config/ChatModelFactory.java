package com.opsvision.harness.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Factory for creating OpenAI ChatModel instances using Spring Boot auto-configuration
 */
@Component
public class ChatModelFactory {
    
    private static final Logger log = LoggerFactory.getLogger(ChatModelFactory.class);
    
    @Autowired
    private ChatModel openAiChatModel; // This will be auto-configured by Spring Boot
    
    /**
     * Create an OpenAI ChatModel instance using Spring Boot auto-configuration
     */
    public ChatModel createChatModel() {
        log.info("Using Spring Boot auto-configured OpenAI ChatModel");
        return openAiChatModel;
    }
}