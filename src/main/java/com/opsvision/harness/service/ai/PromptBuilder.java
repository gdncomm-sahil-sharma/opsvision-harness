package com.opsvision.harness.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.harness.model.dto.SessionContext;
import com.opsvision.harness.model.dto.ToolResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PromptBuilder {
    
    @Autowired
    private ObjectMapper objectMapper;

    public String buildInvestigationPrompt(SessionContext context, String currentQuery) {
        StringBuilder prompt = new StringBuilder();
        
        // System context
        prompt.append("You are a WMS (Warehouse Management System) investigation expert. ");
        prompt.append("Your role is to analyze tool results and provide clear, actionable insights ");
        prompt.append("about warehouse operations, order fulfillment issues, and system problems.\n\n");
        
        // Investigation context
        prompt.append("INVESTIGATION CONTEXT:\n");
        prompt.append("Original Query: ").append(context.getInitialQuery()).append("\n");
        prompt.append("Current Query: ").append(currentQuery).append("\n");
        prompt.append("Session ID: ").append(context.getSessionId()).append("\n\n");
        
        // Previous conversation history
        if (context.getConversationHistory() != null && !context.getConversationHistory().isEmpty()) {
            prompt.append("PREVIOUS CONVERSATION:\n");
            context.getConversationHistory().forEach(conv -> {
                prompt.append("User: ").append(conv.getQuery()).append("\n");
                if (conv.getResponse() != null) {
                    prompt.append("Assistant: ").append(conv.getResponse()).append("\n");
                }
                prompt.append("\n");
            });
        }
        
        // Tool execution results
        if (context.getToolResults() != null && !context.getToolResults().isEmpty()) {
            prompt.append("TOOL EXECUTION RESULTS:\n");
            context.getToolResults().forEach(result -> {
                prompt.append(formatToolResult(result));
                prompt.append("\n");
            });
        }
        
        // Analysis instructions
        prompt.append("ANALYSIS REQUIREMENTS:\n");
        prompt.append("Based on the above data, provide a comprehensive analysis that includes:\n\n");
        prompt.append("1. ROOT CAUSE IDENTIFICATION:\n");
        prompt.append("   - Identify the primary cause of any issues found\n");
        prompt.append("   - Explain the chain of events that led to the problem\n");
        prompt.append("   - Distinguish between symptoms and actual causes\n\n");
        
        prompt.append("2. CONTRIBUTING FACTORS:\n");
        prompt.append("   - List any secondary factors that may have contributed\n");
        prompt.append("   - Identify system, process, or human factors involved\n");
        prompt.append("   - Note any patterns or trends in the data\n\n");
        
        prompt.append("3. IMPACT ASSESSMENT:\n");
        prompt.append("   - Describe the business impact of identified issues\n");
        prompt.append("   - Quantify delays, costs, or operational disruptions where possible\n");
        prompt.append("   - Assess customer impact and service level implications\n\n");
        
        prompt.append("4. RECOMMENDED ACTIONS:\n");
        prompt.append("   - Provide immediate corrective actions to resolve current issues\n");
        prompt.append("   - Suggest preventive measures to avoid recurrence\n");
        prompt.append("   - Prioritize recommendations by urgency and impact\n");
        prompt.append("   - Include monitoring or follow-up suggestions\n\n");
        
        prompt.append("FORMAT:\n");
        prompt.append("- Use clear, professional language suitable for operations teams\n");
        prompt.append("- Structure your response with clear headings and bullet points\n");
        prompt.append("- Reference specific data points from the tool results\n");
        prompt.append("- Be concise but thorough - focus on actionable insights\n");
        prompt.append("- If data is insufficient for analysis, clearly state what additional information is needed\n");
        
        return prompt.toString();
    }

    public String buildContinuationPrompt(SessionContext context, String followUpQuery) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are continuing a WMS investigation conversation. ");
        prompt.append("The user has asked a follow-up question about the ongoing investigation.\n\n");
        
        prompt.append("ORIGINAL INVESTIGATION:\n");
        prompt.append("Initial Query: ").append(context.getInitialQuery()).append("\n");
        prompt.append("Session ID: ").append(context.getSessionId()).append("\n\n");
        
        // Include recent conversation context
        if (context.getConversationHistory() != null && !context.getConversationHistory().isEmpty()) {
            prompt.append("CONVERSATION HISTORY:\n");
            // Show last 3 conversations for context
            List<SessionContext.ConversationSummary> recentConversations = context.getConversationHistory()
                .stream()
                .limit(3)
                .collect(Collectors.toList());
                
            recentConversations.forEach(conv -> {
                prompt.append("User: ").append(conv.getQuery()).append("\n");
                if (conv.getResponse() != null) {
                    prompt.append("Assistant: ").append(truncateResponse(conv.getResponse(), 200)).append("\n");
                }
                prompt.append("\n");
            });
        }
        
        prompt.append("FOLLOW-UP QUESTION: ").append(followUpQuery).append("\n\n");
        
        prompt.append("Please provide a helpful response to the follow-up question, ");
        prompt.append("maintaining context from the previous investigation. ");
        prompt.append("If new tool data is available, incorporate it into your response. ");
        prompt.append("Be concise but comprehensive.\n");
        
        return prompt.toString();
    }

    public String buildSummaryPrompt(SessionContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Provide a concise summary of this WMS investigation session:\n\n");
        prompt.append("Session ID: ").append(context.getSessionId()).append("\n");
        prompt.append("Initial Query: ").append(context.getInitialQuery()).append("\n");
        prompt.append("Duration: ").append(calculateSessionDuration(context)).append("\n\n");
        
        if (context.getConversationHistory() != null) {
            prompt.append("Total Interactions: ").append(context.getConversationHistory().size()).append("\n");
        }
        
        if (context.getToolResults() != null) {
            long successfulTools = context.getToolResults().stream()
                .mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
            prompt.append("Tools Executed: ").append(context.getToolResults().size())
                   .append(" (").append(successfulTools).append(" successful)\n\n");
        }
        
        prompt.append("Please summarize:\n");
        prompt.append("- Key findings from the investigation\n");
        prompt.append("- Main issues identified and their resolution status\n");
        prompt.append("- Outstanding actions or follow-ups needed\n");
        prompt.append("- Overall investigation outcome\n");
        
        return prompt.toString();
    }

    private String formatToolResult(ToolResult result) {
        StringBuilder formatted = new StringBuilder();
        
        formatted.append("Tool: ").append(result.getToolName()).append("\n");
        formatted.append("Status: ").append(result.getStatus()).append("\n");
        formatted.append("Execution Time: ").append(result.getExecutionTimeMs()).append("ms\n");
        
        if (result.isSuccess() && result.getResult() != null) {
            formatted.append("Result Data:\n");
            formatted.append(formatJsonData(result.getResult()));
        } else if (result.getErrorMessage() != null) {
            formatted.append("Error: ").append(result.getErrorMessage()).append("\n");
        }
        
        return formatted.toString();
    }

    private String formatJsonData(JsonNode jsonNode) {
        try {
            if (jsonNode.isTextual()) {
                return jsonNode.asText();
            } else if (jsonNode.isArray()) {
                return String.format("[Array with %d items] %s", 
                                   jsonNode.size(), 
                                   truncateJson(jsonNode.toString(), 500));
            } else if (jsonNode.isObject()) {
                return String.format("[Object with %d fields] %s", 
                                   jsonNode.size(), 
                                   truncateJson(jsonNode.toString(), 500));
            } else {
                return jsonNode.toString();
            }
        } catch (Exception e) {
            return "[Unable to format data]";
        }
    }

    private String truncateJson(String json, int maxLength) {
        if (json.length() <= maxLength) {
            return json;
        }
        return json.substring(0, maxLength) + "... [truncated]";
    }

    private String truncateResponse(String response, int maxLength) {
        if (response == null || response.length() <= maxLength) {
            return response;
        }
        return response.substring(0, maxLength) + "...";
    }

    private String calculateSessionDuration(SessionContext context) {
        if (context.getCreatedAt() != null && context.getUpdatedAt() != null) {
            long minutes = java.time.Duration.between(context.getCreatedAt(), context.getUpdatedAt()).toMinutes();
            return minutes + " minutes";
        }
        return "Unknown";
    }
}