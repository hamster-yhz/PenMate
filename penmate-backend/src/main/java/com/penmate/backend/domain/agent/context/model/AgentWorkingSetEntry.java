package com.penmate.backend.domain.agent.context.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgentWorkingSetEntry(
        Long sessionId,
        Long nodeId,
        BigDecimal activationScore,
        Long lastUsedTurnId,
        Integer useCount,
        Boolean pinned,
        LocalDateTime updatedAt
) {
}
