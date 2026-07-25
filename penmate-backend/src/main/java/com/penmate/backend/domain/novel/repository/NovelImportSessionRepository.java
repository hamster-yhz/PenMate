package com.penmate.backend.domain.novel.repository;

import com.penmate.backend.domain.novel.importing.NovelImportSession;
import com.penmate.backend.domain.novel.importing.NovelImportDraft;

import java.util.List;

public interface NovelImportSessionRepository {
    int insert(NovelImportSession session);
    NovelImportSession findById(Long sessionId);
    NovelImportSession findByIdAndOwner(Long sessionId, Long ownerUserId);
    NovelImportSession lockById(Long sessionId);
    int confirm(Long sessionId, Long ownerUserId, NovelImportDraft draft);
    int attachJob(Long sessionId, Long ownerUserId, Long jobId);
    int markPrepared(Long sessionId, Long projectId);
    int insertVolumeMapping(Long sessionId, int volumeIndex, Long volumeId);
    List<Long> findVolumeIds(Long sessionId);
    int advanceCheckpoint(Long sessionId, int expectedCheckpoint, int nextCheckpoint);
    int markCompleted(Long sessionId);
    int markPaused(Long sessionId, Long ownerUserId);
    int resume(Long sessionId, Long ownerUserId);
    int resetForRetry(Long sessionId, Long ownerUserId);
    int deleteVolumeMappings(Long sessionId);
    int markCancelled(Long sessionId);
    int markFailed(Long sessionId, String message);
    int deleteHiddenProject(Long projectId);
}
