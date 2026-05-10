package com.penmate.backend.application.agent.context;

public record StoryBibleContextResult(
        boolean enabled,
        String source,
        String content
) {

    public StoryBibleContextResult {
        source = source == null || source.isBlank() ? "noop" : source.trim();
        content = content == null ? "" : content;
    }

    public static StoryBibleContextResult noop() {
        return new StoryBibleContextResult(false, "noop", "");
    }
}
