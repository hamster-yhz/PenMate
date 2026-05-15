package com.penmate.backend.application.rag;

/**
 * Structured hybrid retrieval hit used by Context Builder.
 */
public record HybridRagResultView(
        String sourceType,
        String sourceId,
        String content,
        String reason,
        boolean staleFlag,
        Integer matchedVersion,
        double relevanceScore
) {

    public HybridRagResultView {
        sourceType = normalize(sourceType);
        sourceId = normalize(sourceId);
        content = normalize(content);
        reason = normalize(reason);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
