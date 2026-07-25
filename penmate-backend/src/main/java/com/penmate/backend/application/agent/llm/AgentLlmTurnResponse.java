package com.penmate.backend.application.agent.llm;

import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.domain.agent.model.AgentLlmProviderItem;

import java.util.List;

/**
 * LLM 鍗曡疆缁撴瀯鍖栧搷搴斻€?
 */
public record AgentLlmTurnResponse(
        String finishReason,
        String assistantText,
        List<AgentLlmToolCall> toolCalls,
        String rawResponseJson,
        LlmTokenUsage tokenUsage,
        String commentaryText,
        String reasoningSummary,
        List<AgentLlmProviderItem> providerItems
) {

    public AgentLlmTurnResponse(String finishReason,
                                String assistantText,
                                List<AgentLlmToolCall> toolCalls,
                                String rawResponseJson) {
        this(finishReason, assistantText, toolCalls, rawResponseJson, LlmTokenUsage.ZERO,
                "", "", List.of());
    }

    public AgentLlmTurnResponse(String finishReason,
                                String assistantText,
                                List<AgentLlmToolCall> toolCalls,
                                String rawResponseJson,
                                LlmTokenUsage tokenUsage) {
        this(finishReason, assistantText, toolCalls, rawResponseJson, tokenUsage,
                "", "", List.of());
    }

    public AgentLlmTurnResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        assistantText = assistantText == null ? "" : assistantText;
        finishReason = (finishReason == null || finishReason.isBlank()) ? "stop" : finishReason;
        tokenUsage = tokenUsage == null ? LlmTokenUsage.ZERO : tokenUsage;
        commentaryText = commentaryText == null ? "" : commentaryText;
        reasoningSummary = reasoningSummary == null ? "" : reasoningSummary;
        providerItems = providerItems == null ? List.of() : List.copyOf(providerItems);
    }

    public boolean requestsToolCalls() {
        return "tool_calls".equalsIgnoreCase(finishReason) && !toolCalls.isEmpty();
    }
}
