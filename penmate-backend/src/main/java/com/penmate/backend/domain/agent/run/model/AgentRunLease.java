package com.penmate.backend.domain.agent.run.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record AgentRunLease(
        Long runId,
        String owner,
        Long executionToken,
        int attemptCount,
        AgentRunStatus acquiredFrom,
        LocalDateTime expiresAt
) {
    public AgentRunLease {
        runId = Objects.requireNonNull(runId, "runId must not be null");
        owner = Objects.requireNonNull(owner, "owner must not be null");
        executionToken = Objects.requireNonNull(executionToken, "executionToken must not be null");
        acquiredFrom = Objects.requireNonNull(acquiredFrom, "acquiredFrom must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
