package com.penmate.backend.domain.agent.context.repository;

import com.penmate.backend.domain.agent.context.model.AgentWorkingSetEntry;

import java.math.BigDecimal;
import java.util.List;

public interface AgentWorkingSetRepository {
    List<AgentWorkingSetEntry> findBySessionId(Long sessionId);
    int promote(Long sessionId, Long nodeId, BigDecimal score, Long turnId);
    int setPinned(Long sessionId, Long nodeId, boolean pinned);
    int evictExpired(Long sessionId, Long currentTurnId, int retentionTurns);
    int evictOverflow(Long sessionId, int automaticCap);
}
