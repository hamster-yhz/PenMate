package com.penmate.backend.domain.agent.model;

/** Facts required to estimate the next Agent model invocation's context usage. */
public record AgentSessionContextUsageSource(
        Long sessionId,
        Long contextUtf8Bytes,
        Long modelConfigId,
        String modelName,
        Integer maxContextTokens,
        Integer maxOutputTokens,
        String contextCapacitySource,
        Long latestUsageModelConfigId,
        Integer latestInputTokens,
        Integer latestReservedOutputTokens,
        Integer latestProtectedTokens,
        String latestUsageSource
) {
}
