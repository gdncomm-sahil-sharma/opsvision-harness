package com.opsvision.harness.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

    @Bean
    public ChatClient chatClient(GeminiChatModel geminiChatModel) {
        return ChatClient.builder(geminiChatModel).build();
    }
}