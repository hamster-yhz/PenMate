package com.penmate.backend.application.agent.run;

import java.util.List;

public record AgentRunRecoveryResult(
        SessionView session,
        ActiveRunView activeRun,
        Object pendingApproval,
        List<Object> messages,
        Object workbenchContext
) {

    public record SessionView(
            Long sessionId,
            String title,
            String status,
            BoundStyleView boundStyle,
            String lastRunStatus,
            List<String> activeSkills
    ) {
        public SessionView {
            activeSkills = List.copyOf(activeSkills == null ? List.of() : activeSkills);
        }
    }

    public record BoundStyleView(
            Long styleId,
            String name
    ) {
    }

    public record ActiveRunView(
            Long turnId,
            Long runId,
            String runStatus,
            String runPhase,
            Long latestSequence
    ) {
    }
}
