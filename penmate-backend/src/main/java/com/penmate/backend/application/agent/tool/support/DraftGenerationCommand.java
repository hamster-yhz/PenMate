package com.penmate.backend.application.agent.tool.support;

import java.util.List;

/**
 * Draft generation tool 命令对象。
 */
public record DraftGenerationCommand(
        String operation,
        String prompt,
        String sourceText,
        String instruction,
        List<String> preservedConstraints,
        String sourceSummary
) {

    public DraftGenerationCommand {
        operation = operation == null ? "" : operation.trim();
        prompt = prompt == null ? "" : prompt.trim();
        sourceText = sourceText == null ? "" : sourceText.trim();
        instruction = instruction == null ? "" : instruction.trim();
        preservedConstraints = preservedConstraints == null ? List.of() : List.copyOf(preservedConstraints);
        sourceSummary = sourceSummary == null ? "" : sourceSummary.trim();
    }
}
