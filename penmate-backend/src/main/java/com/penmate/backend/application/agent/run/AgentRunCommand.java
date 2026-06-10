package com.penmate.backend.application.agent.run;

public record AgentRunCommand(
        Long projectId,
        Long sessionId,
        Long turnId,
        Long ownerUserId,
        Long runId,
        String taskType,
        String promptSnapshot,
        Long chapterId,
        String selectedText,
        String styleSnapshotJson,
        String modelSnapshotJson,
        String pluginBindingsJson,
        String inputHash,
        String traceId
) {
}
