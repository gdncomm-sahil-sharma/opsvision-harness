package com.opsvision.harness.integration;

import com.opsvision.harness.config.GeminiChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GOOGLE_GENAI_API_KEY", matches = ".+")
class GeminiFullIntegrationTest {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private GeminiChatModel geminiChatModel;

    @Test
    void geminiChatModel_ShouldGenerateResponse_WhenGivenPrompt() {
        // This test only runs if GOOGLE_GENAI_API_KEY environment variable is set
        // Note: This is a live API test and will consume API quota
        
        Prompt prompt = new Prompt("What is 2+2? Respond with just the number.");
        
        ChatResponse response = geminiChatModel.call(prompt);
        
        assertThat(response).isNotNull();
        assertThat(response.getResults()).isNotEmpty();
        assertThat(response.getResult().getOutput().getContent()).isNotBlank();
        
        // The response should contain "4"
        String content = response.getResult().getOutput().getContent();
        assertThat(content).contains("4");
    }

    @Test
    void chatClient_ShouldGenerateResponse_WhenUsingGeminiModel() {
        // Test the ChatClient integration with our custom Gemini model
        
        String response = chatClient.prompt()
                .user("What is the capital of France? Answer with just the city name.")
                .call()
                .content();
        
        assertThat(response).isNotBlank();
        assertThat(response.toLowerCase()).contains("paris");
    }

    @Test
    void geminiChatOptions_ShouldBeApplied_WhenSpecified() {
        // Test that custom options are properly applied
        
        String response = chatClient.prompt()
                .user("Write a very short greeting (max 5 words)")
                .options(GeminiChatModel.GeminiChatOptions.builder()
                        .withTemperature(0.0f)
                        .withMaxOutputTokens(20)
                        .build())
                .call()
                .content();
        
        assertThat(response).isNotBlank();
        // With low temperature and max tokens, response should be short and deterministic
        assertThat(response.split("\\s+")).hasSizeLessThanOrEqualTo(10);
    }
}