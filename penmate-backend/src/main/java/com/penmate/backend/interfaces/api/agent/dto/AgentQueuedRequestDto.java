package com.penmate.backend.interfaces.api.agent.dto;

public record AgentQueuedRequestDto(
        String requestId,
        String type,
        String status,
        String payloadJson,
        Integer attemptCount,
        String createdAt,
        String updatedAt
) {
}
