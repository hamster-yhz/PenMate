package com.penmate.backend.domain.agent.run.model;

import java.time.LocalDateTime;

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
        LocalDateTime verifiedAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
    public boolean verified() {
        return "VERIFIED".equals(archiveStatus);
    }
}
