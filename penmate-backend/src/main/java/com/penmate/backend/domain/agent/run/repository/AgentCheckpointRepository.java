package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;

public interface AgentCheckpointRepository {

    void save(AgentCheckpoint checkpoint);

    AgentCheckpoint findLatest(Long runId);
}
