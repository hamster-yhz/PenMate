package com.penmate.backend.application.agent.runtime;

/**
 * Session 维度 token 使用概览视图。
 */
public record SessionTokenUsageView(
        Integer usedTokens,
        Integer maxContextTokens,
        Double usageRatio,
        Integer promptTokens,
        Integer completionTokens,
        String modelName,
        String usageSource,
        String contextCapacitySource
) {
    public SessionTokenUsageView(Integer usedTokens, Integer maxContextTokens, Double usageRatio,
                                 Integer promptTokens, Integer completionTokens, String modelName) {
        this(usedTokens, maxContextTokens, usageRatio, promptTokens, completionTokens,
                modelName, "ESTIMATE", "FALLBACK");
    }
}
