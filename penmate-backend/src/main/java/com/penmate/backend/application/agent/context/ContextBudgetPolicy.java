package com.penmate.backend.application.agent.context;

/**
 * Context Builder 的最小预算策略。
 */
public final class ContextBudgetPolicy {

    private static final int DEFAULT_MAX_STORY_BIBLE_ENTRIES = 8;

    private final int maxStoryBibleEntries;

    public ContextBudgetPolicy(int maxStoryBibleEntries) {
        this.maxStoryBibleEntries = maxStoryBibleEntries <= 0
                ? DEFAULT_MAX_STORY_BIBLE_ENTRIES
                : maxStoryBibleEntries;
    }

    public int maxStoryBibleEntries() {
        return maxStoryBibleEntries;
    }

    public static ContextBudgetPolicy defaultPolicy() {
        return new ContextBudgetPolicy(DEFAULT_MAX_STORY_BIBLE_ENTRIES);
    }
}
