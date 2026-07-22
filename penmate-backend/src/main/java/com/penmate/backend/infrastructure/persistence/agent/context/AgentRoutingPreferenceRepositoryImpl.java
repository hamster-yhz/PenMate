package com.penmate.backend.infrastructure.persistence.agent.context;

import com.penmate.backend.domain.agent.context.model.AgentRoutingPreference;
import com.penmate.backend.domain.agent.context.repository.AgentRoutingPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AgentRoutingPreferenceRepositoryImpl implements AgentRoutingPreferenceRepository {
    private final AgentRoutingPreferenceMapper mapper;

    @Override public AgentRoutingPreference findProjectPreference(Long projectId) {
        return mapper.findProjectPreference(projectId);
    }

}
