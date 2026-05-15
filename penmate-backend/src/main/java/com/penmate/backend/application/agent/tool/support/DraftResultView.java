package com.penmate.backend.application.agent.tool.support;

import java.util.List;

/**
 * Draft generation tool 结构化输出。
 */
public record DraftResultView(
        String draftText,
        String operation,
        List<String> preservedConstraints,
        String sourceSummary
) {

    public DraftResultView {
        draftText = draftText == null ? "" : draftText;
        operation = operation == null ? "" : operation.trim();
        preservedConstraints = preservedConstraints == null ? List.of() : List.copyOf(preservedConstraints);
        sourceSummary = sourceSummary == null ? "" : sourceSummary.trim();
    }
}
