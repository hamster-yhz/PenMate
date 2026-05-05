package com.penmate.backend.application.agent.tool.definition;

public record ToolApprovalView(
        String toolCode,
        String toolDisplayName,
        Integer riskLevel,
        String approvalType,
        String operationCode
) {
}
