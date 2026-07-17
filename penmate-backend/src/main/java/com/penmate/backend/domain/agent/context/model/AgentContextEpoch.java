package com.penmate.backend.domain.agent.context.model;

import java.time.LocalDateTime;

public record AgentContextEpoch(
        Long epochId,
        Long sessionId,
        Integer epochNo,
        String fingerprint,
        Long storyBibleRevision,
        Long manuscriptRevision,
        Long activeChapterId,
        Long activeChapterContentRevision,
        Long styleBindingRevision,
        String routingMode,
        Long routerModelConfigId,
        Long routerModelConfigRevision,
        String promptBundleHash,
        String skillCatalogHash,
        String toolCatalogHash,
        String snapshotObjectKey,
        String snapshotHash,
        Long snapshotSizeBytes,
        LocalDateTime createdAt,
        LocalDateTime supersededAt
) {
    public AgentContextEpoch(Long epochId, Long sessionId, Integer epochNo, String fingerprint,
                             Long storyBibleRevision, Long manuscriptRevision, Long activeChapterId,
                             Long styleBindingRevision, String routingMode, Long routerModelConfigId,
                             Long routerModelConfigRevision, String promptBundleHash, String skillCatalogHash,
                             String toolCatalogHash, String snapshotObjectKey, String snapshotHash,
                             Long snapshotSizeBytes, LocalDateTime createdAt, LocalDateTime supersededAt) {
        this(epochId, sessionId, epochNo, fingerprint, storyBibleRevision, manuscriptRevision,
                activeChapterId, 0L, styleBindingRevision, routingMode, routerModelConfigId,
                routerModelConfigRevision, promptBundleHash, skillCatalogHash, toolCatalogHash,
                snapshotObjectKey, snapshotHash, snapshotSizeBytes, createdAt, supersededAt);
    }
}
