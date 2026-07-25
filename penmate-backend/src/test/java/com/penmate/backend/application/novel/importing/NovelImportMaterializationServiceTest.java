package com.penmate.backend.application.novel.importing;

import com.penmate.backend.application.rag.ProjectAiConfigurationService;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.domain.novel.importing.NovelImportDraft;
import com.penmate.backend.domain.novel.importing.NovelImportFormat;
import com.penmate.backend.domain.novel.importing.NovelImportSession;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.novel.repository.NovelImportSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NovelImportMaterializationServiceTest {
    private final NovelImportSessionRepository sessions = mock(NovelImportSessionRepository.class);
    private final NovelGateway novels = mock(NovelGateway.class);
    private final BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
    private final StoryBibleApplicationService storyBible = mock(StoryBibleApplicationService.class);
    private final ProjectAiConfigurationService projectAi = mock(ProjectAiConfigurationService.class);
    private final NovelImportMaterializationService service = new NovelImportMaterializationService(
            sessions, novels, ids, storyBible, projectAi);

    @Test
    void prepares_a_hidden_project_and_stable_volume_mapping() {
        NovelImportSession source = session(draft(2, 1), "QUEUED", null, 0);
        NovelImportSession prepared = session(source.getDraft(), "IMPORTING", 9001L, 0);
        AtomicLong sequence = new AtomicLong(9000);
        when(ids.nextId()).thenAnswer(ignored -> sequence.incrementAndGet());
        when(sessions.lockById(7001L)).thenReturn(source, prepared);
        when(novels.insertProject(any())).thenReturn(1);
        when(novels.insertVolume(any())).thenReturn(1);
        when(sessions.markPrepared(7001L, 9001L)).thenReturn(1);

        assertThat(service.prepare(7001L)).isSameAs(prepared);

        ArgumentCaptor<NovelProject> project = ArgumentCaptor.forClass(NovelProject.class);
        verify(novels).insertProject(project.capture());
        assertThat(project.getValue().getStatus()).isZero();
        verify(novels, times(2)).insertVolume(any(NovelVolume.class));
        verify(sessions).insertVolumeMapping(7001L, 0, 9002L);
        verify(sessions).insertVolumeMapping(7001L, 1, 9003L);
    }

    @Test
    void inserts_only_one_batch_and_advances_the_checkpoint_with_it() {
        NovelImportSession source = session(draft(1, 30), "IMPORTING", 9001L, 0);
        AtomicLong sequence = new AtomicLong(10000);
        when(ids.nextId()).thenAnswer(ignored -> sequence.incrementAndGet());
        when(sessions.lockById(7001L)).thenReturn(source);
        when(sessions.findVolumeIds(7001L)).thenReturn(List.of(9101L));
        when(novels.insertChapter(any())).thenReturn(1);
        when(sessions.advanceCheckpoint(7001L, 0, 25)).thenReturn(1);

        assertThat(service.appendNextBatch(7001L, 25)).isEqualTo(25);

        verify(novels, times(25)).insertChapter(any(NovelChapter.class));
        verify(sessions).advanceCheckpoint(7001L, 0, 25);
    }

    private NovelImportSession session(NovelImportDraft draft, String status, Long projectId, int checkpoint) {
        NovelImportSession session = new NovelImportSession();
        session.setSessionId(7001L);
        session.setOwnerUserId(1001L);
        session.setDraft(draft);
        session.setStatus(status);
        session.setProjectId(projectId);
        session.setCheckpointChapter(checkpoint);
        session.setTotalChapters(draft.chapterCount());
        return session;
    }

    private NovelImportDraft draft(int volumeCount, int chaptersPerVolume) {
        return new NovelImportDraft("长夜", NovelImportFormat.TXT,
                IntStream.range(0, volumeCount).mapToObj(volume -> new NovelImportDraft.Volume("第 " + volume + " 卷",
                        IntStream.range(0, chaptersPerVolume)
                                .mapToObj(chapter -> new NovelImportDraft.Chapter("第 " + chapter + " 章", "正文"))
                                .toList())).toList(), List.of());
    }
}
