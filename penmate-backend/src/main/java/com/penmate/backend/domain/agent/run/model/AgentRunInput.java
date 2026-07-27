package com.penmate.backend.domain.agent.run.model;

import java.util.List;
import java.util.Objects;

public record AgentRunInput(
        Long runId,
        String promptSnapshot,
        Long chapterId,
        List<Long> chapterIds,
        String selectedText,
        String styleSnapshotJson,
        String modelSnapshotJson,
        String pluginBindingsJson,
        String safetyMode,
        String inputHash
) {

    public AgentRunInput {
        runId = Objects.requireNonNull(runId, "runId must not be null");
        promptSnapshot = promptSnapshot == null ? "" : promptSnapshot;
        chapterIds = chapterIds == null ? List.of() : chapterIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        chapterId = chapterIds.isEmpty() ? null : chapterIds.getFirst();
        safetyMode = com.penmate.backend.domain.agent.model.AgentSafetyMode.parse(safetyMode).name();
        inputHash = inputHash == null || inputHash.isBlank() ? "unhashed" : inputHash;
    }
}
