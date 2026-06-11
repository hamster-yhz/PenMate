package com.penmate.backend.domain.agent.run.model;

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