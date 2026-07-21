package com.penmate.backend.interfaces.api.agent.dto;

import java.util.List;

public record AgentRunHistoryDto(
        String runId,
        String turnId,
        String predecessorRunId,
        String runStatus,
        String runPhase,
        Integer attemptCount,
        String lastErrorCode,
        String lastErrorMessage,
        String latestSequence,
        String startedAt,
        String finishedAt,
        OutputDto output,
        List<AgentRunEventDto> events
) {
    public record OutputDto(
            String text,
            Long offset,
            String sequence,
            String state,
            String updatedAt
    ) { }
}
