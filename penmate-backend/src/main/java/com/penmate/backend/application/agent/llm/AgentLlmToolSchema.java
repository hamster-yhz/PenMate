package com.penmate.backend.application.agent.llm;

/**
 * LLM 可见工具 schema。
 */
public record AgentLlmToolSchema(
        String toolCode,
        String description,
        String parametersJsonSchema
) {
}
