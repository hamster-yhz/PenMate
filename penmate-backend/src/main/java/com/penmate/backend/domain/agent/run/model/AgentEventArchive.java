package com.penmate.backend.domain.agent.run.model;

import java.time.Instant;

public record AgentEventArchive(
        Long archiveId,
        Long runId,
        Long firstSequence,
        Long lastSequence,
        Integer eventCount,
        String objectKey,
        Long sizeBytes,
        String sha256,
        String archiveStatus,
        Instant verifiedAt,
        Instant expiresAt,
        Instant createdAt
) {
    public boolean verified() {
        return "VERIFIED".equals(archiveStatus);
    }
}
