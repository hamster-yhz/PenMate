package com.penmate.backend.application.agent.tool.runtime;

public record ToolCallResult(
        String status,
        Long approvalId,
        String toolOutput,
        String errorCode,
        String errorMessage
) {
    public static ToolCallResult waitingApproval(Long approvalId) {
        return new ToolCallResult("WAITING_APPROVAL", approvalId, null, null, null);
    }

    public static ToolCallResult success(String toolOutput) {
        return new ToolCallResult("SUCCESS", null, toolOutput, null, null);
    }
}
