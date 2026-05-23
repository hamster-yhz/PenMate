package com.penmate.backend.application.agent.llm;

import lombok.Builder;

/**
 * Agent 单次模型调用执行配置。
 */
@Builder
public record AgentLlmExecutionConfig(
        Long modelConfigId,
        String providerCode,
        String baseUrl,
        String apiKey,
        String modelName,
        String keySource,
        Integer contextWindowTurns) {
}
