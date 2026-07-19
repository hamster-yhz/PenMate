package com.penmate.backend.domain.agent.context.model;

public record AgentRoutingPreference(
        Long projectId,
        String storyBibleRoutingMode,
        Long routerModelConfigId,
        Long embeddingModelConfigId,
        String indexStatus
) {
}
