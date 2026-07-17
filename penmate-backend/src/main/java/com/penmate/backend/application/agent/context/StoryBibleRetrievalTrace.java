package com.penmate.backend.application.agent.context;

import java.util.List;

public record StoryBibleRetrievalTrace(
        boolean semanticRetrieverAvailable,
        int alwaysIncludeCount,
        int exactAliasCount,
        int lexicalCandidateCount,
        int semanticCandidateCount,
        int mergedCandidateCount,
        List<Candidate> candidates
) {
    public static final StoryBibleRetrievalTrace EMPTY = new StoryBibleRetrievalTrace(
            false, 0, 0, 0, 0, 0, List.of());

    public StoryBibleRetrievalTrace {
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
    }

    public record Candidate(Long nodeId, double score, List<String> reasons) {
        public Candidate {
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
        }
    }
}
