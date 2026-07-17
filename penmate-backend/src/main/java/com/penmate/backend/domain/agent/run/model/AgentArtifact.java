package com.penmate.backend.domain.agent.run.model;

import java.time.LocalDateTime;

public record AgentArtifact(
        Long artifactId,
        Long runId,
        Long eventId,
        String artifactType,
        String payloadJson,
        Integer sizeBytes,
        LocalDateTime createdAt
) {}