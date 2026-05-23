package com.penmate.backend.application.agent.llm;

/**
 * LLM 单轮或聚合 token 用量。
 */
public record LlmTokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {

    public static final LlmTokenUsage ZERO = new LlmTokenUsage(0, 0, 0);

    public LlmTokenUsage add(LlmTokenUsage other) {
        if (other == null) {
            return this;
        }
        return new LlmTokenUsage(
                promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                totalTokens + other.totalTokens
        );
    }
}
