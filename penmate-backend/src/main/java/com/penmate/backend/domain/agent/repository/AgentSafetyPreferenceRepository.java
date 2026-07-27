package com.penmate.backend.domain.agent.repository;

import com.penmate.backend.domain.agent.model.AgentSafetyMode;

public interface AgentSafetyPreferenceRepository {
    AgentSafetyMode findByUserId(Long userId);
    int upsert(Long userId, AgentSafetyMode mode);
}
