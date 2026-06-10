package com.penmate.backend.interfaces.api.agent.dto;

public record AgentRunEventDto(
        String eventId,
        String runId,
        String projectId,
        String sessionId,
        String turnId,
        String sequence,
        Integer schemaVersion,
        String type,
        String payloadJson,
        String createdAt
) {
}
