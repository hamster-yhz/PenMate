package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentArtifact;

public interface AgentArtifactRepository {
    void save(AgentArtifact artifact);
    AgentArtifact findById(Long artifactId);
}