package com.penmate.backend.domain.agent.run.model;

import java.util.Objects;

public record AgentRunInput(
        Long runId,
        String promptSnapshot,
        String taskType,
        Long chapterId,
        String selectedText,
        String styleSnapshotJson,
        String modelSnapshotJson,
        String pluginBindingsJson,
        String inputHash
) {

    public AgentRunInput {
        runId = Objects.requireNonNull(runId, "runId must not be null");
        promptSnapshot = promptSnapshot == null ? "" : promptSnapshot;
        taskType = taskType == null || taskType.isBlank() ? "CHAT" : taskType;
        inputHash = inputHash == null || inputHash.isBlank() ? "unhashed" : inputHash;
    }
}
