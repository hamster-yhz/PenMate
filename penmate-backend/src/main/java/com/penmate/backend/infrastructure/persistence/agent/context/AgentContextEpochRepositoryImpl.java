package com.penmate.backend.infrastructure.persistence.agent.context;

import com.penmate.backend.domain.agent.context.model.AgentContextEpoch;
import com.penmate.backend.domain.agent.context.repository.AgentContextEpochRepository;
import org.springframework.stereotype.Repository;

import java.util.Objects;

@Repository
public class AgentContextEpochRepositoryImpl implements AgentContextEpochRepository {

    private final AgentContextEpochMapper mapper;

    public AgentContextEpochRepositoryImpl(AgentContextEpochMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override public AgentContextEpoch findCurrentByFingerprint(Long sessionId, String fingerprint) { return mapper.findCurrentByFingerprint(sessionId, fingerprint); }
    @Override public AgentContextEpoch findById(Long epochId) { return mapper.findById(epochId); }
    @Override public int nextEpochNo(Long sessionId) { return mapper.nextEpochNo(sessionId); }
    @Override public int insert(AgentContextEpoch epoch) { return mapper.insert(epoch); }
    @Override public int supersedeCurrent(Long sessionId, Long nextEpochId) { return mapper.supersedeCurrent(sessionId, nextEpochId); }
    @Override public int bindSession(Long sessionId, Long epochId) { return mapper.bindSession(sessionId, epochId); }
    @Override public int bindRun(Long runId, Long epochId) { return mapper.bindRun(runId, epochId); }
}
