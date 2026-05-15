package com.penmate.backend.application.storybible;

public record StoryBibleProposalItem(
        String entryKey,
        String entryType,
        String proposedContent,
        String canonicalStatus,
        int riskLevel,
        String sourceText,
        Long sourceChapterId,
        String inferenceLevel
) {

    public StoryBibleProposalItem {
        entryKey = normalize(entryKey);
        entryType = normalize(entryType);
        proposedContent = normalize(proposedContent);
        canonicalStatus = normalizeUpper(canonicalStatus, "PROPOSED");
        riskLevel = riskLevel <= 0 ? 1 : riskLevel;
        sourceText = normalize(sourceText);
        inferenceLevel = normalize(inferenceLevel);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeUpper(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? fallback : normalized.toUpperCase();
    }
}
