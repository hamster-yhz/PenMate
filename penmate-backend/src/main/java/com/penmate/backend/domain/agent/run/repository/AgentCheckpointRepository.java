package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;

import java.time.LocalDateTime;
import java.util.List;

public interface AgentCheckpointRepository {

    void save(AgentCheckpoint checkpoint);

    AgentCheckpoint findLatest(Long runId);

    List<AgentCheckpoint> findLatest(Long runId, int limit);

    int deleteOlderThanLatest(Long runId, int keep);

    int deleteTerminalOlderThan(LocalDateTime cutoff);
}
