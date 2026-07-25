package com.penmate.backend.interfaces.api.agent.dto;

import java.util.List;

public record AgentRecoverySnapshotDto(
        SessionDto session,
        ActiveRunDto activeRun,
        Object pendingApproval,
        List<Object> messages,
        Object workbenchContext
) {

    public record SessionDto(
            String sessionId,
            String title,
            String status,
            BoundStyleDto boundStyle,
            String lastRunStatus,
            List<String> activeSkills
    ) {
        public SessionDto {
            activeSkills = List.copyOf(activeSkills == null ? List.of() : activeSkills);
        }
    }

    public record BoundStyleDto(
            String styleId,
            String name
    ) {
    }

    public record ActiveRunDto(
            String turnId,
            String runId,
            String runStatus,
            String runPhase,
            String latestSequence
    ) {
    }
}
