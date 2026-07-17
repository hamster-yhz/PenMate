package com.penmate.backend.application.agent.run;

public record AgentRunResult(
        Long runId,
        String runStatus,
        String runPhase,
        Long latestSequence
) {
}
