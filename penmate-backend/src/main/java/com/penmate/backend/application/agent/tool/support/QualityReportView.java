package com.penmate.backend.application.agent.tool.support;

import java.util.List;
import java.util.Map;

/**
 * 质量审查结构化输出。
 */
public record QualityReportView(
        Integer score,
        List<String> passes,
        List<Map<String, String>> issues,
        boolean needsRevision,
        List<String> riskFlags,
        List<RevisionSuggestionView> revisionSuggestions,
        Integer currentRevisionRound,
        Integer maxRevisionRounds,
        boolean revisionAllowed,
        String reviewSummary
) {

    public QualityReportView {
        score = score == null ? 0 : score;
        passes = passes == null ? List.of() : List.copyOf(passes);
        issues = issues == null ? List.of() : List.copyOf(issues);
        riskFlags = riskFlags == null ? List.of() : List.copyOf(riskFlags);
        revisionSuggestions = revisionSuggestions == null ? List.of() : List.copyOf(revisionSuggestions);
        currentRevisionRound = currentRevisionRound == null ? 0 : currentRevisionRound;
        maxRevisionRounds = maxRevisionRounds == null ? 0 : maxRevisionRounds;
        reviewSummary = reviewSummary == null ? "" : reviewSummary.trim();
    }
}
