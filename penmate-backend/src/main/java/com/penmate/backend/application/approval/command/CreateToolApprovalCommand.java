package com.penmate.backend.application.approval.command;

public record CreateToolApprovalCommand(
        Long runId,
        Long projectId,
        Long sessionId,
        Long turnId,
        String toolCallId,
        String toolCode,
        String toolArgsJson,
        String toolContextJson,
        String resumePayloadJson,
        String idempotencyKey,
        Long operatorId,
        String traceId,
        String approvalBindingJson
) {
}
