package com.penmate.backend.application.agent.context;

import java.util.List;

/** Test and explicit fallback implementation; production uses pgvector. */
public class NoopStoryBibleSemanticRetriever implements StoryBibleSemanticRetriever {
    @Override
    public SemanticResult retrieve(Long projectId, Long storyBibleId, String query, int limit) {
        return new SemanticResult(false, List.of());
    }

    public SemanticResult retrieve(Long storyBibleId, String query, int limit) {
        return retrieve(null, storyBibleId, query, limit);
    }
}
