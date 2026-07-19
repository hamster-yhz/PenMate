package com.penmate.backend.domain.agent.run.model;

import java.time.Instant;

public record AgentArtifact(
        Long artifactId,
        Long runId,
        Long eventId,
        String artifactType,
        String payloadJson,
        Integer sizeBytes,
        Instant createdAt
) {}