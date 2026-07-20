package com.penmate.backend.application.agent.context;

import java.util.List;

public interface StoryBibleSemanticRetriever {
    SemanticResult retrieve(Long projectId, Long storyBibleId, String query, int limit);

    record SemanticResult(boolean available, List<StoryBibleCandidateRetriever.Candidate> candidates) {
        public SemanticResult {
            candidates = List.copyOf(candidates == null ? List.of() : candidates);
        }
    }
}
