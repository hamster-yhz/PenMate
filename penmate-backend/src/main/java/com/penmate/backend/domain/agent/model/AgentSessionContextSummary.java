package com.penmate.backend.domain.agent.model;

import java.time.Instant;

public record AgentSessionContextSummary(
        Long sessionId,
        Long projectId,
        Long ownerUserId,
        String summaryJson,
        Integer cutoffMessageSeq,
        Integer promptTokens,
        Integer completionTokens,
        Instant updatedAt
) {
}
