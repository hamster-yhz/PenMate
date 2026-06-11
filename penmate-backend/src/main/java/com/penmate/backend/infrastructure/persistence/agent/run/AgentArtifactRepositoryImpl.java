package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import org.springframework.stereotype.Repository;

@Repository
public class AgentArtifactRepositoryImpl implements AgentArtifactRepository {

    private final AgentArtifactMapper artifactMapper;

    public AgentArtifactRepositoryImpl(AgentArtifactMapper artifactMapper) {
        this.artifactMapper = artifactMapper;
    }

    @Override
    public void save(AgentArtifact artifact) {
        artifactMapper.insert(artifact);
    }

    @Override
    public AgentArtifact findById(Long artifactId) {
        return artifactMapper.findById(artifactId);
    }
}