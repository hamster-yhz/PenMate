package com.penmate.backend.domain.agent.run.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record AgentCheckpoint(
        Long checkpointId,
        Long runId,
        Long checkpointNo,
        Long lastEventSeq,
        String stateJson,
        Integer stateSizeBytes,
        Integer stateSchemaVersion,
        String stateSha256,
        String stateObjectKey,
        LocalDateTime createdAt
) {

    public AgentCheckpoint {
        checkpointId = Objects.requireNonNull(checkpointId, "checkpointId must not be null");
        runId = Objects.requireNonNull(runId, "runId must not be null");
        checkpointNo = Objects.requireNonNull(checkpointNo, "checkpointNo must not be null");
        lastEventSeq = Objects.requireNonNull(lastEventSeq, "lastEventSeq must not be null");
        stateJson = stateJson == null ? "{}" : stateJson;
        stateSizeBytes = stateSizeBytes == null ? stateJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length : stateSizeBytes;
        stateSchemaVersion = stateSchemaVersion == null ? 1 : stateSchemaVersion;
    }

    public AgentCheckpoint(Long checkpointId, Long runId, Long checkpointNo, Long lastEventSeq,
                           String stateJson, Integer stateSizeBytes, LocalDateTime createdAt) {
        this(checkpointId, runId, checkpointNo, lastEventSeq, stateJson, stateSizeBytes,
                1, null, null, createdAt);
    }
}
