package com.penmate.backend.application.agent.run;

import java.util.List;

public record AgentRunCommand(
        Long projectId,
        Long sessionId,
        Long turnId,
        Long ownerUserId,
        Long runId,
        String promptSnapshot,
        Long chapterId,
        List<Long> chapterIds,
        String selectedText,
        String styleSnapshotJson,
        String modelSnapshotJson,
        String pluginBindingsJson,
        String safetyMode,
        String inputHash,
        String traceId
) {
}
