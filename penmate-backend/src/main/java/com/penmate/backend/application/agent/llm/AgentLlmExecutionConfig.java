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
        Integer contextWindowTurns,
        String protocolCode,
        AgentReasoningPolicy reasoningPolicy,
        Integer maxContextTokens,
        Integer maxOutputTokens) {

    public AgentLlmExecutionConfig(Long modelConfigId,
                                   String providerCode,
                                   String baseUrl,
                                   String apiKey,
                                   String modelName,
                                   String keySource,
                                   Integer contextWindowTurns) {
        this(modelConfigId, providerCode, baseUrl, apiKey, modelName, keySource,
                contextWindowTurns, null, AgentReasoningPolicy.AUTO, 128_000, 8_192);
    }

    public AgentLlmExecutionConfig {
        reasoningPolicy = reasoningPolicy == null ? AgentReasoningPolicy.AUTO : reasoningPolicy;
        maxContextTokens = maxContextTokens == null || maxContextTokens <= 0 ? 128_000 : maxContextTokens;
        maxOutputTokens = maxOutputTokens == null || maxOutputTokens <= 0 ? 8_192 : maxOutputTokens;
    }
}
