package com.penmate.backend.application.agent.context;

import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DefaultAgentContextRoutingFacade implements AgentContextRoutingFacade {

    private final StoryBibleContextProvider storyBibleContextProvider;

    public DefaultAgentContextRoutingFacade(StoryBibleContextProvider storyBibleContextProvider) {
        this.storyBibleContextProvider = Objects.requireNonNull(storyBibleContextProvider, "storyBibleContextProvider");
    }

    @Override
    public AgentContextRoutingResult route(AgentContextRoutingRequest request) {
        Objects.requireNonNull(request, "request");
        String styleSnapshot = request.decision().includeStyleContext() ? request.styleSnapshot() : null;
        StoryBibleContextResult storyBibleContext = request.decision().includeStoryBibleContext()
                ? storyBibleContextProvider.loadContext(
                        request.projectId(),
                        request.conversationId(),
                        request.chapterId(),
                        request.userMessage(),
                        request.decision()
                )
                : StoryBibleContextResult.noop();
        return new AgentContextRoutingResult(styleSnapshot, storyBibleContext);
    }
}
