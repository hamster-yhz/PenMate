package com.penmate.backend.domain.agent.run.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record AgentRun(
        Long runId,
        Long projectId,
        Long sessionId,
        Long turnId,
        Long ownerUserId,
        String runStatus,
        String runPhase,
        Long contextEpochId,
        Long activeApprovalId,
        String leaseOwner,
        LocalDateTime leaseUntil,
        Long executionToken,
        Integer attemptCount,
        LocalDateTime nextRetryAt,
        String lastErrorCode,
        String lastErrorMessage,
        Long latestEventSeq,
        Long latestCheckpointId,
        String traceId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {

    public AgentRun {
        runId = Objects.requireNonNull(runId, "runId must not be null");
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        turnId = Objects.requireNonNull(turnId, "turnId must not be null");
        ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        runStatus = runStatus == null || runStatus.isBlank() ? "PENDING" : runStatus;
        runPhase = runPhase == null || runPhase.isBlank() ? "created" : runPhase;
        latestEventSeq = latestEventSeq == null ? 0L : latestEventSeq;
        executionToken = executionToken == null ? 0L : executionToken;
        attemptCount = attemptCount == null ? 0 : attemptCount;
    }

    public AgentRun(Long runId, Long projectId, Long sessionId, Long turnId, Long ownerUserId,
                    String runStatus, String runPhase, Long contextEpochId, Long activeApprovalId,
                    Long latestEventSeq, Long latestCheckpointId, String traceId,
                    LocalDateTime startedAt, LocalDateTime finishedAt) {
        this(runId, projectId, sessionId, turnId, ownerUserId, runStatus, runPhase, contextEpochId,
                activeApprovalId, null, null, 0L, 0, null, null, null, latestEventSeq,
                latestCheckpointId, traceId, startedAt, finishedAt);
    }

    public AgentRunStatus status() {
        return AgentRunStatus.from(runStatus);
    }
}
