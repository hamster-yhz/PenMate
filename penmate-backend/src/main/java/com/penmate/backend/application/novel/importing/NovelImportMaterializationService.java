package com.penmate.backend.application.novel.importing;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.rag.ProjectAiConfigurationService;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.domain.novel.importing.NovelImportDraft;
import com.penmate.backend.domain.novel.importing.NovelImportSession;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.novel.repository.NovelImportSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class NovelImportMaterializationService {
    private final NovelImportSessionRepository sessions;
    private final NovelGateway novels;
    private final BusinessIdGenerator ids;
    private final StoryBibleApplicationService storyBible;
    private final ProjectAiConfigurationService projectAiConfiguration;

    public NovelImportMaterializationService(NovelImportSessionRepository sessions,
                                             NovelGateway novels,
                                             BusinessIdGenerator ids,
                                             StoryBibleApplicationService storyBible,
                                             ProjectAiConfigurationService projectAiConfiguration) {
        this.sessions = sessions;
        this.novels = novels;
        this.ids = ids;
        this.storyBible = storyBible;
        this.projectAiConfiguration = projectAiConfiguration;
    }

    @Transactional
    public NovelImportSession prepare(Long sessionId) {
        NovelImportSession session = requireImportable(sessions.lockById(sessionId));
        if (session.getProjectId() != null) return session;
        NovelImportDraft draft = session.getDraft();
        draft.validateForImport();

        NovelProject project = new NovelProject();
        project.setProjectId(ids.nextId());
        project.setOwnerUserId(session.getOwnerUserId());
        project.setTitle(draft.projectTitle());
        project.setGenre("其他");
        project.setTags(List.of());
        project.setStatus(0);
        project.setStructureRevision(1L);
        requireOne(novels.insertProject(project), "Failed to create import project shell");

        for (int index = 0; index < draft.volumes().size(); index++) {
            NovelVolume volume = new NovelVolume();
            volume.setVolumeId(ids.nextId());
            volume.setProjectId(project.getProjectId());
            volume.setTitle(draft.volumes().get(index).title());
            volume.setSortOrder(index + 1);
            requireOne(novels.insertVolume(volume), "Failed to create imported volume");
            sessions.insertVolumeMapping(sessionId, index, volume.getVolumeId());
        }
        requireOne(sessions.markPrepared(sessionId, project.getProjectId()), "Import session state changed");
        return sessions.lockById(sessionId);
    }

    @Transactional
    public int appendNextBatch(Long sessionId, int batchSize) {
        NovelImportSession session = requireImportable(sessions.lockById(sessionId));
        if (session.getProjectId() == null) throw BusinessException.conflict("Import project is not prepared");
        int checkpoint = session.getCheckpointChapter() == null ? 0 : session.getCheckpointChapter();
        List<IndexedChapter> chapters = flatten(session.getDraft());
        if (checkpoint >= chapters.size()) return checkpoint;
        List<Long> volumeIds = sessions.findVolumeIds(sessionId);
        if (volumeIds.size() != session.getDraft().volumes().size()) {
            throw BusinessException.conflict("Import volume mapping is incomplete");
        }
        int next = Math.min(chapters.size(), checkpoint + Math.max(1, batchSize));
        for (int index = checkpoint; index < next; index++) {
            IndexedChapter source = chapters.get(index);
            NovelChapter chapter = new NovelChapter();
            chapter.setChapterId(ids.nextId());
            chapter.setProjectId(session.getProjectId());
            chapter.setVolumeId(volumeIds.get(source.volumeIndex()));
            chapter.setTitle(source.chapter().title());
            chapter.setContent(source.chapter().content());
            chapter.setWordCount(source.chapter().content().codePoints()
                    .filter(codePoint -> !Character.isWhitespace(codePoint)).toArray().length);
            chapter.setSortOrder(source.chapterIndex() + 1);
            requireOne(novels.insertChapter(chapter), "Failed to insert imported chapter");
        }
        requireOne(sessions.advanceCheckpoint(sessionId, checkpoint, next), "Import checkpoint changed");
        return next;
    }

    @Transactional
    public Long publish(Long sessionId) {
        NovelImportSession session = requireImportable(sessions.lockById(sessionId));
        if (session.getCheckpointChapter() == null || !session.getCheckpointChapter().equals(session.getTotalChapters())) {
            throw BusinessException.conflict("Import chapters are incomplete");
        }
        NovelProject project = new NovelProject();
        project.setProjectId(session.getProjectId());
        project.setOwnerUserId(session.getOwnerUserId());
        project.setTitle(session.getDraft().projectTitle());
        project.setGenre("其他");
        project.setTags(List.of());
        project.setStatus(1);
        project.setStructureRevision(1L);
        requireOne(novels.updateProject(project), "Failed to publish imported project");
        storyBible.bootstrap(project.getProjectId(), project.getTitle(), session.getOwnerUserId());
        projectAiConfiguration.initializeProject(project.getProjectId(), session.getOwnerUserId());
        requireOne(sessions.markCompleted(sessionId), "Failed to complete import session");
        return project.getProjectId();
    }

    @Transactional
    public void cancelAndCleanup(Long sessionId, boolean failed, String message) {
        NovelImportSession session = sessions.lockById(sessionId);
        if (session == null || "COMPLETED".equals(session.getStatus())) return;
        if (session.getProjectId() != null) sessions.deleteHiddenProject(session.getProjectId());
        if (failed) sessions.markFailed(sessionId, truncate(message));
        else sessions.markCancelled(sessionId);
    }

    private NovelImportSession requireImportable(NovelImportSession session) {
        if (session == null) throw BusinessException.notFound("Import session not found");
        if (!List.of("QUEUED", "IMPORTING").contains(session.getStatus())) {
            throw BusinessException.conflict("Import session is not running");
        }
        return session;
    }

    private List<IndexedChapter> flatten(NovelImportDraft draft) {
        List<IndexedChapter> result = new ArrayList<>();
        for (int volumeIndex = 0; volumeIndex < draft.volumes().size(); volumeIndex++) {
            var chapters = draft.volumes().get(volumeIndex).chapters();
            for (int chapterIndex = 0; chapterIndex < chapters.size(); chapterIndex++) {
                result.add(new IndexedChapter(volumeIndex, chapterIndex, chapters.get(chapterIndex)));
            }
        }
        return result;
    }

    private void requireOne(int affected, String message) {
        if (affected != 1) throw BusinessException.conflict(message);
    }

    private String truncate(String value) {
        String normalized = value == null || value.isBlank() ? "Import failed" : value;
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    private record IndexedChapter(int volumeIndex, int chapterIndex, NovelImportDraft.Chapter chapter) { }
}
