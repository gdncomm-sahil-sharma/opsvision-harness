package com.opsvision.harness.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

    private static final Logger log = LoggerFactory.getLogger(SpringAiConfig.class);

    @Value("${spring.ai.mcp.enabled:false}")
    private boolean mcpEnabled;

    @Bean
    public ChatModel chatModel(ChatModelFactory chatModelFactory) {
        return chatModelFactory.createChatModel();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {

        log.info("Creating ChatClient with MCP auto-configuration: {}", mcpEnabled);

        return ChatClient.builder(chatModel)
            .defaultSystem("You are a warehouse management system assistant. " +
                         "Use the available functions to get warehouse data when requested. " +
                         "Always use functions when asked about order information.")
            .build();
    }

    /**
     * Wraps the JPA-backed {@link ChatMemoryRepository} in a windowed memory.
     * Defining this bean overrides Spring AI's auto-configured in-memory
     * default, so per-session chat history survives app restarts.
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository,
                                 @Value("${app.chat.memory.max-messages:20}") int maxMessages) {
        log.info("Creating MessageWindowChatMemory: maxMessages={} repo={}",
                maxMessages, chatMemoryRepository.getClass().getSimpleName());
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }
}