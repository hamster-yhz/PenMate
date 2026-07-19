package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;

import java.time.Instant;
import java.util.List;

public interface AgentCheckpointRepository {

    void save(AgentCheckpoint checkpoint);

    AgentCheckpoint findLatest(Long runId);

    List<AgentCheckpoint> findLatest(Long runId, int limit);

    int deleteOlderThanLatest(Long runId, int keep);

    List<AgentCheckpoint> findTerminalHotBefore(Instant cutoff, int limit);

    int markCold(Long checkpointId, String stateJson, String stateObjectKey, String stateSha256,
                 Instant archivedAt, Instant expiresAt);

    List<AgentCheckpoint> findExpiredCold(Instant now, int limit);

    int deleteCold(Long checkpointId);
}
