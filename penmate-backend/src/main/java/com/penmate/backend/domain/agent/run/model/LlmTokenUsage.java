package com.penmate.backend.domain.agent.run.model;

public record LlmTokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int cachedPromptTokens,
        int cacheCreationPromptTokens
) {

    public static final LlmTokenUsage ZERO = new LlmTokenUsage(0, 0, 0, 0, 0);

    public LlmTokenUsage(int promptTokens, int completionTokens, int totalTokens) {
        this(promptTokens, completionTokens, totalTokens, 0, 0);
    }

    public LlmTokenUsage add(LlmTokenUsage other) {
        if (other == null) {
            return this;
        }
        return new LlmTokenUsage(
                promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                totalTokens + other.totalTokens,
                cachedPromptTokens + other.cachedPromptTokens,
                cacheCreationPromptTokens + other.cacheCreationPromptTokens
        );
    }

    /** Logical input tokens occupying the provider context window. Cache fields are informational subsets. */
    public int contextInputTokens() {
        return Math.max(0, promptTokens);
    }
}
