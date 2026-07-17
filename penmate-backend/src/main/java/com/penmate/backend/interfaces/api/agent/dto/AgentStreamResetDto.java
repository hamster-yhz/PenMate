package com.penmate.backend.interfaces.api.agent.dto;

public record AgentStreamResetDto(
        String runId,
        String requestedAfter,
        String oldestAvailableSequence,
        String latestSequence,
        String reason
) {
}
