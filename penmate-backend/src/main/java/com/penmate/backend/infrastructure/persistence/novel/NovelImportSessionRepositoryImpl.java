package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.novel.importing.NovelImportDraft;
import com.penmate.backend.domain.novel.importing.NovelImportSession;
import com.penmate.backend.domain.novel.repository.NovelImportSessionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class NovelImportSessionRepositoryImpl implements NovelImportSessionRepository {
    private final NovelImportSessionMapper mapper;
    private final JsonCodec json;

    public NovelImportSessionRepositoryImpl(NovelImportSessionMapper mapper, JsonCodec json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public int insert(NovelImportSession session) {
        NovelImportSessionMapper.Row row = new NovelImportSessionMapper.Row();
        row.sessionId = session.getSessionId();
        row.ownerUserId = session.getOwnerUserId();
        row.sourceFormat = session.getDraft().sourceFormat().name();
        row.originalFilename = session.getOriginalFilename();
        row.draftJson = json.write(session.getDraft());
        row.status = session.getStatus();
        row.totalChapters = session.getDraft().chapterCount();
        return mapper.insert(row);
    }

    @Override public NovelImportSession findById(Long sessionId) { return map(mapper.findById(sessionId)); }
    @Override public NovelImportSession findByIdAndOwner(Long sessionId, Long ownerUserId) { return map(mapper.findByIdAndOwner(sessionId, ownerUserId)); }
    @Override public NovelImportSession lockById(Long sessionId) { return map(mapper.lockById(sessionId)); }

    @Override
    public int confirm(Long sessionId, Long ownerUserId, NovelImportDraft draft) {
        return mapper.confirm(sessionId, ownerUserId, json.write(draft), draft.chapterCount());
    }

    @Override public int attachJob(Long sessionId, Long ownerUserId, Long jobId) { return mapper.attachJob(sessionId, ownerUserId, jobId); }
    @Override public int markPrepared(Long sessionId, Long projectId) { return mapper.markPrepared(sessionId, projectId); }
    @Override public int insertVolumeMapping(Long sessionId, int volumeIndex, Long volumeId) { return mapper.insertVolumeMapping(sessionId, volumeIndex, volumeId); }
    @Override public List<Long> findVolumeIds(Long sessionId) { return mapper.findVolumeIds(sessionId); }
    @Override public int advanceCheckpoint(Long sessionId, int expectedCheckpoint, int nextCheckpoint) { return mapper.advanceCheckpoint(sessionId, expectedCheckpoint, nextCheckpoint); }
    @Override public int markCompleted(Long sessionId) { return mapper.markCompleted(sessionId); }
    @Override public int markPaused(Long sessionId, Long ownerUserId) { return mapper.markPaused(sessionId, ownerUserId); }
    @Override public int resume(Long sessionId, Long ownerUserId) { return mapper.resume(sessionId, ownerUserId); }
    @Override public int resetForRetry(Long sessionId, Long ownerUserId) { return mapper.resetForRetry(sessionId, ownerUserId); }
    @Override public int deleteVolumeMappings(Long sessionId) { return mapper.deleteVolumeMappings(sessionId); }
    @Override public int markCancelled(Long sessionId) { return mapper.markCancelled(sessionId); }
    @Override public int markFailed(Long sessionId, String message) { return mapper.markFailed(sessionId, message); }

    @Override
    public int deleteHiddenProject(Long projectId) {
        mapper.deleteProjectChapters(projectId);
        mapper.deleteProjectVolumes(projectId);
        return mapper.deleteHiddenProject(projectId);
    }

    private NovelImportSession map(NovelImportSessionMapper.Row row) {
        if (row == null) return null;
        NovelImportSession session = new NovelImportSession();
        session.setSessionId(row.sessionId);
        session.setOwnerUserId(row.ownerUserId);
        session.setOriginalFilename(row.originalFilename);
        session.setDraft(json.read(row.draftJson, NovelImportDraft.class));
        session.setStatus(row.status);
        session.setProjectId(row.projectId);
        session.setJobId(row.jobId);
        session.setCheckpointChapter(row.checkpointChapter);
        session.setTotalChapters(row.totalChapters);
        session.setErrorMessage(row.errorMessage);
        session.setCreatedAt(row.createdAt);
        session.setUpdatedAt(row.updatedAt);
        return session;
    }
}
