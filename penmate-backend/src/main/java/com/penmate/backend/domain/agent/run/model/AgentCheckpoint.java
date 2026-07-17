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
        String storageTier,
        LocalDateTime coldArchivedAt,
        LocalDateTime expiresAt,
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
        storageTier = storageTier == null || storageTier.isBlank() ? "HOT" : storageTier;
        if (!storageTier.equals("HOT") && !storageTier.equals("COLD")) {
            throw new IllegalArgumentException("Unsupported checkpoint storage tier: " + storageTier);
        }
        if (storageTier.equals("COLD") && (stateObjectKey == null || stateObjectKey.isBlank())) {
            throw new IllegalArgumentException("Cold checkpoint must reference an object");
        }
    }

    public AgentCheckpoint(Long checkpointId, Long runId, Long checkpointNo, Long lastEventSeq,
                           String stateJson, Integer stateSizeBytes, Integer stateSchemaVersion,
                           String stateSha256, String stateObjectKey, LocalDateTime createdAt) {
        this(checkpointId, runId, checkpointNo, lastEventSeq, stateJson, stateSizeBytes,
                stateSchemaVersion, stateSha256, stateObjectKey, "HOT", null, null, createdAt);
    }

    public AgentCheckpoint(Long checkpointId, Long runId, Long checkpointNo, Long lastEventSeq,
                           String stateJson, Integer stateSizeBytes, LocalDateTime createdAt) {
        this(checkpointId, runId, checkpointNo, lastEventSeq, stateJson, stateSizeBytes,
                1, null, null, "HOT", null, null, createdAt);
    }

    public boolean isCold() {
        return "COLD".equals(storageTier);
    }
}
