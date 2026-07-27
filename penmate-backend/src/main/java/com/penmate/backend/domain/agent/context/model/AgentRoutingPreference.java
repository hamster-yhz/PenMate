package com.penmate.backend.domain.agent.context.model;

public record AgentRoutingPreference(
        Long projectId,
        String storyBibleRoutingMode,
        boolean ragEnabled,
        Long routerModelConfigId,
        Long embeddingModelConfigId,
        String indexStatus
) {
}
