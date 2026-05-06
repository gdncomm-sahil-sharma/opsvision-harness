package com.opsvision.harness.model.dto;

import com.opsvision.harness.model.dto.response.ChatResponseData;

import java.util.UUID;

/**
 * Bundles the resolved chatId with the structured response from one
 * non-streaming chat turn. Returned by
 * {@code AIAssistantService.generateStructuredResponse}; the controller
 * unpacks both into {@code StructuredChatResponse} on the way out.
 *
 * <p>{@code chatId} may be null only on the early-failure path where the
 * chat couldn't be resolved (e.g. a generic exception before
 * {@code SessionService.getOrCreateChat} returned). In the normal flow
 * it's always populated, including on LLM-side failures.
 */
public record ChatTurnResult(UUID chatId, ChatResponseData data) {}
