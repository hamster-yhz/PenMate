package com.penmate.backend.infrastructure.persistence.agent.context;

import com.penmate.backend.domain.agent.context.model.AgentWorkingSetEntry;
import com.penmate.backend.domain.agent.context.repository.AgentWorkingSetRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class AgentWorkingSetRepositoryImpl implements AgentWorkingSetRepository {
    private final AgentWorkingSetMapper mapper;

    public AgentWorkingSetRepositoryImpl(AgentWorkingSetMapper mapper) {
        this.mapper = mapper;
    }

    @Override public List<AgentWorkingSetEntry> findBySessionId(Long sessionId) { return mapper.findBySessionId(sessionId); }
    @Override public int promote(Long sessionId, Long nodeId, BigDecimal score, Long turnId) { return mapper.promote(sessionId, nodeId, score, turnId); }
    @Override public int setPinned(Long sessionId, Long nodeId, boolean pinned) { return mapper.setPinned(sessionId, nodeId, pinned); }
    @Override public int evictExpired(Long sessionId, Long currentTurnId, int retentionTurns) { return mapper.evictExpired(sessionId, currentTurnId, retentionTurns); }
    @Override public int evictOverflow(Long sessionId, int automaticCap) { return mapper.evictOverflow(sessionId, automaticCap); }
}
