package com.penmate.backend.infrastructure.persistence.agent.context;

import com.penmate.backend.domain.agent.context.model.AgentRoutingPreference;
import com.penmate.backend.domain.agent.context.repository.AgentRoutingPreferenceRepository;
import org.springframework.stereotype.Repository;

@Repository
public class AgentRoutingPreferenceRepositoryImpl implements AgentRoutingPreferenceRepository {
    private final AgentRoutingPreferenceMapper mapper;

    public AgentRoutingPreferenceRepositoryImpl(AgentRoutingPreferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override public AgentRoutingPreference findUserPreference(Long userId) { return mapper.findUserPreference(userId); }
    @Override public int upsertUserPreference(AgentRoutingPreference preference) { return mapper.upsertUserPreference(preference); }
    @Override public int updateSessionOverride(Long projectId, Long sessionId, String routingMode, Long routerModelConfigId) {
        return mapper.updateSessionOverride(projectId, sessionId, routingMode, routerModelConfigId);
    }
}
