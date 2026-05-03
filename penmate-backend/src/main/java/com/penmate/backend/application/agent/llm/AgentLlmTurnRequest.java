package com.penmate.backend.application.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM 单轮对话请求。
 */
public record AgentLlmTurnRequest(
        List<Map<String, Object>> messages,
        List<AgentLlmToolSchema> tools,
        String toolChoice
) {

    public AgentLlmTurnRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        toolChoice = (toolChoice == null || toolChoice.isBlank()) ? "auto" : toolChoice.trim();
    }
}
