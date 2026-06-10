package com.penmate.backend.domain.agent.run.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record AgentEvent(
        Long eventId,
        Long runId,
        Long projectId,
        Long sessionId,
        Long turnId,
        Long sequence,
        Integer schemaVersion,
        String eventType,
        String payloadJson,
        LocalDateTime createdAt
) {

    public AgentEvent {
        eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        runId = Objects.requireNonNull(runId, "runId must not be null");
        sequence = Objects.requireNonNull(sequence, "sequence must not be null");
        schemaVersion = schemaVersion == null ? 1 : schemaVersion;
        eventType = requireText(eventType, "eventType");
        payloadJson = payloadJson == null ? "{}" : payloadJson;
    }

    public static AgentEvent replay(Long eventId, Long runId, Long sequence, String eventType, String payloadJson) {
        return new AgentEvent(eventId, runId, null, null, null, sequence, 1, eventType, payloadJson, null);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
