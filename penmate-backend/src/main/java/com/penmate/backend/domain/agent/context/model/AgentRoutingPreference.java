package com.penmate.backend.domain.agent.context.model;

public record AgentRoutingPreference(
        Long userId,
        String storyBibleRoutingMode,
        Long routerModelConfigId
) {
}
