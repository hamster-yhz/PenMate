package com.penmate.backend.application.agent.usecase;

public record AgentTurnResult(
        Long sessionId,
        ActiveRunView activeRun
) {

    public record ActiveRunView(
            Long turnId,
            Long runId,
            String runStatus,
            String runPhase,
            Long latestSequence
    ) {
        public ActiveRunView(Long turnId, Long runId, String runStatus) {
            this(turnId, runId, runStatus, null, null);
        }
    }

    public static AgentTurnResult forRun(Long runId, String runStatus) {
        return new AgentTurnResult(null, new ActiveRunView(null, runId, runStatus));
    }

    public static AgentTurnResult forRun(Long sessionId,
                                         Long turnId,
                                         Long runId,
                                         String runStatus,
                                         String runPhase,
                                         Long latestSequence) {
        return new AgentTurnResult(
                sessionId,
                new ActiveRunView(turnId, runId, runStatus, runPhase, latestSequence)
        );
    }
}
