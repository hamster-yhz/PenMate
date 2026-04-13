package com.penmate.backend.application.approval.command;

public record CreateApprovalCommand(
        Long projectId,
        Long taskId,
        String approvalType,
        String payloadJson,
        Integer riskLevel,
        Long requestedBy
) {
}

