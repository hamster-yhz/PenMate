package com.penmate.backend.domain.agent.context.repository;

import com.penmate.backend.domain.agent.context.model.AgentRoutingPreference;

public interface AgentRoutingPreferenceRepository {
    AgentRoutingPreference findUserPreference(Long userId);
    int upsertUserPreference(AgentRoutingPreference preference);
    int updateSessionOverride(Long projectId, Long sessionId, String routingMode, Long routerModelConfigId);
}
