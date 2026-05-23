package com.penmate.backend.application.agent.llm;

import java.util.List;

/**
 * LLM 单轮结构化响应。
 */
public record AgentLlmTurnResponse(
        String finishReason,
        String assistantText,
        List<AgentLlmToolCall> toolCalls,
        String rawResponseJson,
        LlmTokenUsage tokenUsage
) {

    public AgentLlmTurnResponse(String finishReason,
                                String assistantText,
                                List<AgentLlmToolCall> toolCalls,
                                String rawResponseJson) {
        this(finishReason, assistantText, toolCalls, rawResponseJson, LlmTokenUsage.ZERO);
    }

    public AgentLlmTurnResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        assistantText = assistantText == null ? "" : assistantText;
        finishReason = (finishReason == null || finishReason.isBlank()) ? "stop" : finishReason;
        tokenUsage = tokenUsage == null ? LlmTokenUsage.ZERO : tokenUsage;
    }

    public boolean requestsToolCalls() {
        return "tool_calls".equalsIgnoreCase(finishReason) && !toolCalls.isEmpty();
    }
}
