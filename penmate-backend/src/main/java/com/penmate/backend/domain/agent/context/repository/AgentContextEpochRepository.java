package com.penmate.backend.domain.agent.context.repository;

import com.penmate.backend.domain.agent.context.model.AgentContextEpoch;

public interface AgentContextEpochRepository {

    Long lockSession(Long sessionId);

    AgentContextEpoch findCurrentByFingerprint(Long sessionId, String fingerprint);

    AgentContextEpoch findById(Long epochId);

    int nextEpochNo(Long sessionId);

    int insert(AgentContextEpoch epoch);

    int supersedeCurrent(Long sessionId, Long nextEpochId);

    int bindSession(Long sessionId, Long epochId);

    int bindRun(Long runId, Long epochId);
}
