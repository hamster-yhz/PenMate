package com.penmate.backend.application.agent.llm;

/**
 * LLM 返回的单次工具调用。
 */
public record AgentLlmToolCall(
        String id,
        String toolCode,
        String argumentsJson
) {
}
