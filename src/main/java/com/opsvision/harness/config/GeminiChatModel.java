package com.opsvision.harness.config;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class GeminiChatModel implements ChatModel {

    private final Client client;
    private final String modelName;
    private final Float defaultTemperature;
    private final Integer defaultMaxTokens;

    public GeminiChatModel(@Value("${spring.ai.google.genai.api-key}") String apiKey,
                          @Value("${spring.ai.google.genai.chat.options.model:gemini-2.0-flash-exp}") String modelName,
                          @Value("${spring.ai.google.genai.chat.options.temperature:0.1}") Float temperature,
                          @Value("${spring.ai.google.genai.chat.options.max-output-tokens:2000}") Integer maxTokens) {
        
        this.modelName = modelName;
        this.defaultTemperature = temperature;
        this.defaultMaxTokens = maxTokens;
        
        // Create the client
        this.client = Client.builder()
                .apiKey(apiKey)
                .build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            // Combine all messages into a single prompt string
            String combinedPrompt = prompt.getInstructions().stream()
                .map(Message::getContent)
                .collect(Collectors.joining("\n"));

            // Create generation config
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .temperature(defaultTemperature)
                    .maxOutputTokens(defaultMaxTokens)
                    .build();

            // Generate content
            GenerateContentResponse response = client.models.generateContent(modelName, combinedPrompt, config);

            // Convert response to Spring AI format
            if (response != null && response.text() != null && !response.text().isEmpty()) {
                Generation generation = new Generation(response.text());
                return new ChatResponse(List.of(generation));
            } else {
                throw new RuntimeException("No response from Gemini API");
            }

        } catch (Exception e) {
            throw new RuntimeException("Error calling Gemini API: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return GeminiChatOptions.builder()
                .withModel(modelName)
                .withTemperature(defaultTemperature)
                .withMaxOutputTokens(defaultMaxTokens)
                .build();
    }

    // Custom ChatOptions implementation for Gemini
    public static class GeminiChatOptions implements ChatOptions {
        private String model;
        private Float temperature;
        private Integer maxOutputTokens;

        public static Builder builder() {
            return new Builder();
        }

        public String getModel() { return model; }
        public Float getTemperature() { return temperature; }
        public Integer getMaxOutputTokens() { return maxOutputTokens; }

        // Required by ChatOptions interface
        @Override
        public Integer getTopK() { return null; }

        @Override
        public Float getTopP() { return null; }

        public static class Builder {
            private GeminiChatOptions options = new GeminiChatOptions();

            public Builder withModel(String model) {
                options.model = model;
                return this;
            }

            public Builder withTemperature(Float temperature) {
                options.temperature = temperature;
                return this;
            }

            public Builder withMaxOutputTokens(Integer maxOutputTokens) {
                options.maxOutputTokens = maxOutputTokens;
                return this;
            }

            public GeminiChatOptions build() {
                return options;
            }
        }
    }
}