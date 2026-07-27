package com.penmate.backend.interfaces.api.agent.dto;

public record AgentRunDto(
        AgentRecoverySnapshotDto.SessionDto session,
        ActiveRunDto activeRun,
        String userMessage
) {

    public record ActiveRunDto(
            String turnId,
            String runId,
            String runStatus,
            String runPhase,
            String latestSequence
    ) {
    }
}
