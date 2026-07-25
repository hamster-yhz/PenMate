package com.penmate.backend.application.novel.importing;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.ops.AsyncJobQueueService;
import com.penmate.backend.domain.novel.importing.NovelImportDraft;
import com.penmate.backend.domain.novel.importing.NovelImportFormat;
import com.penmate.backend.domain.novel.importing.NovelImportSession;
import com.penmate.backend.domain.novel.repository.NovelImportSessionRepository;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.repository.OpsRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NovelImportApplicationService {
    private final Map<NovelImportFormat, NovelImportSourceParser> parsers;
    private final NovelImportSessionRepository sessions;
    private final OpsRepository ops;
    private final AsyncJobQueueService jobs;
    private final NovelImportMaterializationService materialization;
    private final BusinessIdGenerator ids;
    private final JsonCodec json;

    public NovelImportApplicationService(List<NovelImportSourceParser> parsers,
                                         NovelImportSessionRepository sessions,
                                         OpsRepository ops,
                                         AsyncJobQueueService jobs,
                                         NovelImportMaterializationService materialization,
                                         BusinessIdGenerator ids,
                                         JsonCodec json) {
        this.parsers = new EnumMap<>(NovelImportFormat.class);
        parsers.forEach(parser -> this.parsers.put(parser.format(), parser));
        this.sessions = sessions;
        this.ops = ops;
        this.jobs = jobs;
        this.materialization = materialization;
        this.ids = ids;
        this.json = json;
    }

    @Transactional
    public PreviewResult preview(Long ownerUserId, String filename, InputStream input) {
        NovelImportFormat format;
        try {
            format = NovelImportFormat.fromFilename(filename);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest(exception.getMessage());
        }
        NovelImportSourceParser parser = parsers.get(format);
        if (parser == null) throw BusinessException.badRequest("No parser is available for " + format);
        NovelImportDraft draft;
        try {
            draft = parser.parse(filename, input);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest(exception.getMessage());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read import file", exception);
        }
        if (draft.chapterCount() == 0) throw BusinessException.badRequest("No chapters were found in the file");
        NovelImportSession session = new NovelImportSession();
        session.setSessionId(ids.nextId());
        session.setOwnerUserId(ownerUserId);
        session.setOriginalFilename(filename == null ? "import" : filename);
        session.setDraft(draft);
        session.setStatus("DRAFT");
        if (sessions.insert(session) != 1) throw BusinessException.of("Failed to create import preview");
        return new PreviewResult(session.getSessionId(), draft);
    }

    @Transactional
    public SessionView confirm(Long ownerUserId, Long sessionId, NovelImportDraft adjustedDraft) {
        NovelImportSession existing = requireOwned(sessionId, ownerUserId);
        if (!"DRAFT".equals(existing.getStatus())) throw BusinessException.conflict("Import was already confirmed");
        NovelImportDraft normalized = new NovelImportDraft(adjustedDraft.projectTitle(),
                existing.getDraft().sourceFormat(), adjustedDraft.volumes(), List.of()).withDiagnostics();
        try {
            normalized.validateForImport();
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest(exception.getMessage());
        }
        if (sessions.confirm(sessionId, ownerUserId, normalized) != 1) {
            throw BusinessException.conflict("Import preview changed");
        }
        OpsAsyncJob job = jobs.enqueue("NOVEL_IMPORT", "novel:import:" + sessionId,
                ownerUserId, null, json.write(Map.of("sessionId", sessionId)));
        if (sessions.attachJob(sessionId, ownerUserId, job.getJobId()) != 1) {
            throw BusinessException.conflict("Failed to attach import job");
        }
        return get(ownerUserId, sessionId);
    }

    public SessionView get(Long ownerUserId, Long sessionId) {
        NovelImportSession session = requireOwned(sessionId, ownerUserId);
        OpsAsyncJob job = session.getJobId() == null ? null : ops.findJobById(session.getJobId());
        return view(session, job);
    }

    @Transactional
    public SessionView pause(Long ownerUserId, Long sessionId) {
        NovelImportSession session = requireOwned(sessionId, ownerUserId);
        if (session.getJobId() == null || sessions.markPaused(sessionId, ownerUserId) != 1) {
            throw BusinessException.conflict("Import cannot be paused");
        }
        jobs.requestCancel(session.getJobId());
        return get(ownerUserId, sessionId);
    }

    @Transactional
    public SessionView resume(Long ownerUserId, Long sessionId) {
        NovelImportSession session = requireOwned(sessionId, ownerUserId);
        if (!"PAUSED".equals(session.getStatus()) || session.getJobId() == null) {
            throw BusinessException.conflict("Import is not paused");
        }
        jobs.retry(session.getJobId());
        if (sessions.resume(sessionId, ownerUserId) != 1) throw BusinessException.conflict("Import cannot be resumed");
        return get(ownerUserId, sessionId);
    }

    @Transactional
    public SessionView cancel(Long ownerUserId, Long sessionId) {
        NovelImportSession session = requireOwned(sessionId, ownerUserId);
        if (session.getJobId() != null) jobs.requestCancel(session.getJobId());
        materialization.cancelAndCleanup(sessionId, false, null);
        return get(ownerUserId, sessionId);
    }

    @Transactional
    public SessionView retry(Long ownerUserId, Long sessionId) {
        NovelImportSession session = requireOwned(sessionId, ownerUserId);
        if (session.getJobId() == null || !List.of("FAILED", "CANCELLED").contains(session.getStatus())) {
            throw BusinessException.conflict("Import cannot be retried");
        }
        sessions.deleteVolumeMappings(sessionId);
        if (sessions.resetForRetry(sessionId, ownerUserId) != 1) throw BusinessException.conflict("Import cannot be retried");
        jobs.retry(session.getJobId());
        return get(ownerUserId, sessionId);
    }

    private NovelImportSession requireOwned(Long sessionId, Long ownerUserId) {
        NovelImportSession session = sessions.findByIdAndOwner(sessionId, ownerUserId);
        if (session == null) throw BusinessException.notFound("Import session not found");
        return session;
    }

    private SessionView view(NovelImportSession session, OpsAsyncJob job) {
        return new SessionView(session.getSessionId(), session.getStatus(), session.getProjectId(),
                session.getJobId(), session.getCheckpointChapter(), session.getTotalChapters(),
                job == null ? null : job.getStatus(), job == null ? null : job.getProgressCurrent(),
                job == null ? null : job.getProgressTotal(), job == null ? null : job.getProgressMessage(),
                session.getErrorMessage() != null ? session.getErrorMessage()
                        : job == null ? null : job.getLastErrorMessage());
    }

    public record PreviewResult(Long sessionId, NovelImportDraft draft) { }
    public record SessionView(Long sessionId, String status, Long projectId, Long jobId,
                              Integer checkpointChapter, Integer totalChapters,
                              String jobStatus, Long progressCurrent, Long progressTotal,
                              String progressMessage, String errorMessage) { }
}
