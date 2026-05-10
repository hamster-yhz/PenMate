package com.penmate.backend.application.agent.context;

import java.util.Objects;

public record AgentContextRoutingResult(
        String styleSnapshot,
        StoryBibleContextResult storyBibleContext
) {

    public AgentContextRoutingResult {
        storyBibleContext = storyBibleContext == null ? StoryBibleContextResult.noop() : storyBibleContext;
    }
}
