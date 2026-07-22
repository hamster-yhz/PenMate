package com.penmate.backend.domain.agent.context.repository;

import com.penmate.backend.domain.agent.context.model.AgentRoutingPreference;

public interface AgentRoutingPreferenceRepository {
    AgentRoutingPreference findProjectPreference(Long projectId);
}
