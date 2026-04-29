package com.penmate.backend.application.agent;

public record ToolInvocationGatewayResult(
        String status,
        Long approvalId,
        String toolOutput,
        String errorCode,
        String errorMessage
) {
    public static ToolInvocationGatewayResult waitingApproval(Long approvalId) {
        return new ToolInvocationGatewayResult("WAITING_APPROVAL", approvalId, null, null, null);
    }

    public static ToolInvocationGatewayResult success(String toolOutput) {
        return new ToolInvocationGatewayResult("SUCCESS", null, toolOutput, null, null);
    }
}
