package com.penmate.backend.application.agent.llm;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;

import java.util.List;

/**
 * LLM 单轮对话请求。
 */
public record AgentLlmTurnRequest(
        List<AgentLlmMessage> messages,
        List<AgentLlmToolSchema> tools,
        String toolChoice
) {

    public AgentLlmTurnRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        toolChoice = (toolChoice == null || toolChoice.isBlank()) ? "auto" : toolChoice.trim();
    }
}
