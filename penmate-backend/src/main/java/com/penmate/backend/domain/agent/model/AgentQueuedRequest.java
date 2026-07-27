package com.penmate.backend.domain.agent.model;

import java.time.Instant;

public record AgentQueuedRequest(
        Long requestId,
        Long projectId,
        Long sessionId,
        Long ownerUserId,
        String requestType,
        String payloadJson,
        String requestStatus,
        Integer attemptCount,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
}
