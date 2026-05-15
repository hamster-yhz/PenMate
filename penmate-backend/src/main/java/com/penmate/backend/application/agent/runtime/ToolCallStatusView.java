package com.penmate.backend.application.agent.runtime;

/**
 * 工具调用运行态视图。
 */
public record ToolCallStatusView(
        String toolCallId,
        String toolCode,
        String toolName,
        String status,
        Integer iteration,
        Object argumentsPreview,
        Object output,
        String errorMessage
) {
}
