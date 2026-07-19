package com.penmate.backend.domain.agent.run.model;

import java.time.Instant;
import java.util.Objects;

public record AgentRunPendingApproval(
        Long id,
        Long pendingApprovalId,
        Long approvalId,
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
        String pendingStatus,
        Long operatorId,
        String traceId,
        Instant createdAt,
        Instant updatedAt
) {

    public AgentRunPendingApproval {
        approvalId = Objects.requireNonNull(approvalId, "approvalId must not be null");
        runId = Objects.requireNonNull(runId, "runId must not be null");
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        turnId = Objects.requireNonNull(turnId, "turnId must not be null");
        toolCallId = toolCallId == null || toolCallId.isBlank() ? "unknown" : toolCallId.trim();
        toolCode = toolCode == null || toolCode.isBlank() ? "unknown" : toolCode.trim();
        idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? runId + ":" + toolCallId
                : idempotencyKey.trim();
        pendingStatus = pendingStatus == null || pendingStatus.isBlank() ? "PENDING" : pendingStatus.trim();
    }
}
