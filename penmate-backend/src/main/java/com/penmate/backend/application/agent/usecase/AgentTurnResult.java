package com.penmate.backend.application.agent.usecase;

public record AgentTurnResult(
        Long sessionId,
        ActiveRunView activeRun
) {

    public record ActiveRunView(
            Long turnId,
            Long runId,
            String runStatus
    ) {
    }

    public static AgentTurnResult forRun(Long runId, String runStatus) {
        return new AgentTurnResult(null, new ActiveRunView(null, runId, runStatus));
    }
}
