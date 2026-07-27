package com.penmate.backend.application.agent.safety;

import com.penmate.backend.domain.agent.model.AgentSafetyMode;
import com.penmate.backend.domain.agent.repository.AgentSafetyPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentSafetyModeApplicationService {
    private final AgentSafetyPreferenceRepository preferences;

    @Transactional(readOnly = true)
    public AgentSafetyMode get(Long userId) { return preferences.findByUserId(userId); }

    @Transactional
    public AgentSafetyMode save(Long userId, String mode) {
        AgentSafetyMode parsed = AgentSafetyMode.parse(mode);
        if (preferences.upsert(userId, parsed) != 1) throw new IllegalStateException("Failed to save Agent safety mode");
        return parsed;
    }
}
