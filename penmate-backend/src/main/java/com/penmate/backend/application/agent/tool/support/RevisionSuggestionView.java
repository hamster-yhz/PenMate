package com.penmate.backend.application.agent.tool.support;

/**
 * 质量审查后的修订建议。
 */
public record RevisionSuggestionView(
        String priority,
        String target,
        String instruction,
        String rationale
) {

    public RevisionSuggestionView {
        priority = priority == null ? "" : priority.trim();
        target = target == null ? "" : target.trim();
        instruction = instruction == null ? "" : instruction.trim();
        rationale = rationale == null ? "" : rationale.trim();
    }
}
