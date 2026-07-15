package com.penmate.backend.application.agent.context;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoopStoryBibleSemanticRetriever implements StoryBibleSemanticRetriever {
    @Override
    public SemanticResult retrieve(Long storyBibleId, String query, int limit) {
        return new SemanticResult(false, List.of());
    }
}
