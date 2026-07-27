package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentSafetyMode;
import com.penmate.backend.domain.agent.repository.AgentSafetyPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AgentSafetyPreferenceRepositoryImpl implements AgentSafetyPreferenceRepository {
    private final AgentSafetyPreferenceMapper mapper;
    @Override public AgentSafetyMode findByUserId(Long userId) {
        String value = mapper.findByUserId(userId);
        return value == null ? AgentSafetyMode.STANDARD : AgentSafetyMode.parse(value);
    }
    @Override public int upsert(Long userId, AgentSafetyMode mode) { return mapper.upsert(userId, mode.name()); }
}
