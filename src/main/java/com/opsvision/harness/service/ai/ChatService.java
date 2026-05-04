package com.opsvision.harness.service.ai;

import com.opsvision.harness.model.dto.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import com.opsvision.harness.config.GeminiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    
    @Autowired
    private ChatClient chatClient;
    
    @Autowired
    private PromptBuilder promptBuilder;

    public String generateInvestigation(SessionContext context, String currentQuery) {
        log.info("Generating AI investigation response for session: {}", context.getSessionId());
        
        try {
            String prompt = promptBuilder.buildInvestigationPrompt(context, currentQuery);
            
            log.debug("Built investigation prompt with length: {} characters", prompt.length());
            
            String response = chatClient.prompt()
                .user(prompt)
                .options(GeminiChatModel.GeminiChatOptions.builder()
                    .withModel("gemini-2.0-flash-exp")
                    .withTemperature(0.1f)
                    .withMaxOutputTokens(2000)
                    .build())
                .call()
                .content();
                
            log.info("Generated AI response with length: {} characters", response.length());
            return response;
            
        } catch (Exception e) {
            log.error("Failed to generate AI investigation response: {}", e.getMessage(), e);
            return generateFallbackResponse(context, currentQuery, e);
        }
    }

    public String generateFollowUpResponse(SessionContext context, String followUpQuery) {
        log.info("Generating AI follow-up response for session: {}", context.getSessionId());
        
        try {
            String prompt = promptBuilder.buildContinuationPrompt(context, followUpQuery);
            
            String response = chatClient.prompt()
                .user(prompt)
                .options(GeminiChatModel.GeminiChatOptions.builder()
                    .withModel("gemini-2.0-flash-exp")
                    .withTemperature(0.2f)
                    .withMaxOutputTokens(1500)
                    .build())
                .call()
                .content();
                
            log.info("Generated AI follow-up response with length: {} characters", response.length());
            return response;
            
        } catch (Exception e) {
            log.error("Failed to generate AI follow-up response: {}", e.getMessage(), e);
            return generateFallbackResponse(context, followUpQuery, e);
        }
    }

    public String generateSessionSummary(SessionContext context) {
        log.info("Generating session summary for session: {}", context.getSessionId());
        
        try {
            String prompt = promptBuilder.buildSummaryPrompt(context);
            
            String response = chatClient.prompt()
                .user(prompt)
                .options(GeminiChatModel.GeminiChatOptions.builder()
                    .withModel("gemini-2.0-flash-exp")
                    .withTemperature(0.1f)
                    .withMaxOutputTokens(1000)
                    .build())
                .call()
                .content();
                
            log.info("Generated session summary with length: {} characters", response.length());
            return response;
            
        } catch (Exception e) {
            log.error("Failed to generate session summary: {}", e.getMessage(), e);
            return "Unable to generate session summary due to AI service error.";
        }
    }

    public boolean isHealthy() {
        try {
            String testResponse = chatClient.prompt()
                .user("Respond with 'OK' if you can process this request.")
                .options(GeminiChatModel.GeminiChatOptions.builder()
                    .withModel("gemini-2.0-flash-exp")
                    .withTemperature(0.0f)
                    .withMaxOutputTokens(10)
                    .build())
                .call()
                .content();
                
            return testResponse != null && testResponse.toLowerCase().contains("ok");
            
        } catch (Exception e) {
            log.warn("AI service health check failed: {}", e.getMessage());
            return false;
        }
    }

    private String generateFallbackResponse(SessionContext context, String query, Exception error) {
        log.warn("Using fallback response generation due to AI service error");
        
        StringBuilder fallback = new StringBuilder();
        fallback.append("Investigation Analysis (Automated Fallback)\n\n");
        
        fallback.append("Query: ").append(query).append("\n");
        fallback.append("Session ID: ").append(context.getSessionId()).append("\n\n");
        
        // Basic tool results summary
        if (context.getToolResults() != null && !context.getToolResults().isEmpty()) {
            long successful = context.getToolResults().stream()
                .mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
            long failed = context.getToolResults().size() - successful;
            
            fallback.append("Tool Execution Summary:\n");
            fallback.append("- Total tools executed: ").append(context.getToolResults().size()).append("\n");
            fallback.append("- Successful: ").append(successful).append("\n");
            fallback.append("- Failed: ").append(failed).append("\n\n");
            
            if (successful > 0) {
                fallback.append("Data was successfully retrieved from ").append(successful)
                       .append(" tool(s). Please review the tool results above for detailed information.\n\n");
            }
            
            if (failed > 0) {
                fallback.append("Note: ").append(failed)
                       .append(" tool(s) failed to execute. This may limit the completeness of the analysis.\n\n");
            }
        }
        
        fallback.append("Recommendations:\n");
        fallback.append("- Review the individual tool results for specific data points\n");
        fallback.append("- Contact the operations team if critical issues are identified\n");
        fallback.append("- Retry the investigation if tool failures occurred\n\n");
        
        fallback.append("Note: This is a fallback response due to AI service unavailability. ");
        fallback.append("For detailed analysis, please try again when the AI service is restored.\n");
        
        return fallback.toString();
    }
}