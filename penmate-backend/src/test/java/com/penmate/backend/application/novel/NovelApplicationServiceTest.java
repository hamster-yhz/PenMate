package com.penmate.backend.application.novel;

import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.application.novel.command.NovelCommands.CreateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.ImportChapterCommand;
import com.penmate.backend.application.novel.command.NovelCommands.ImportProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.ImportVolumeCommand;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.application.rag.ProjectAiConfigurationService;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.ChapterAiUndoOperation;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class NovelApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private NovelGateway novelGateway;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

    @Mock
    private StoryBibleApplicationService storyBibleApplicationService;

    @Mock
    private ProjectAiConfigurationService projectAiConfigurationService;

    @InjectMocks
    private NovelApplicationService novelApplicationService;

    @Test
    void UT_APP_NOVEL_LIST_PROJECTS_SUCCESS() {
        when(novelGateway.findAllProjects()).thenReturn(List.of(new NovelProject(), new NovelProject()));
        assertThat(novelApplicationService.listProjects()).hasSize(2);
        verify(novelGateway).findAllProjects();
    }

    @Test
    void UT_APP_NOVEL_GET_PROJECT_NOT_FOUND() {
        when(novelGateway.findProjectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> novelApplicationService.getProject(1L))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Project not found");
    }

    @Test
    void UT_APP_NOVEL_CREATE_PROJECT_DEFAULT_STATUS_AND_GENERATED_ID() {
        when(businessIdGenerator.nextId()).thenReturn(900001L, 900011L, 900021L);
        when(novelGateway.insertProject(any(NovelProject.class))).thenReturn(1);
        when(novelGateway.insertVolume(any(NovelVolume.class))).thenReturn(1);
        when(novelGateway.insertChapter(any(NovelChapter.class))).thenReturn(1);

        NovelProject created = novelApplicationService.createProject(
                new CreateProjectCommand(920001L, "新建项目", "摘要", null),
                "trace-create-project"
        );

        assertThat(created.getProjectId()).isEqualTo(900001L);
        assertThat(created.getStatus()).isEqualTo(1);
        assertThat(created.getGenre()).isEqualTo("其他");
        verify(novelGateway).insertProject(any(NovelProject.class));
        ArgumentCaptor<NovelVolume> volumeCaptor = ArgumentCaptor.forClass(NovelVolume.class);
        verify(novelGateway).insertVolume(volumeCaptor.capture());
        assertThat(volumeCaptor.getValue()).satisfies(volume -> {
            assertThat(volume.getVolumeId()).isEqualTo(900011L);
            assertThat(volume.getProjectId()).isEqualTo(900001L);
            assertThat(volume.getTitle()).isEqualTo("第一卷");
        });
        ArgumentCaptor<NovelChapter> chapterCaptor = ArgumentCaptor.forClass(NovelChapter.class);
        verify(novelGateway).insertChapter(chapterCaptor.capture());
        assertThat(chapterCaptor.getValue()).satisfies(chapter -> {
            assertThat(chapter.getChapterId()).isEqualTo(900021L);
            assertThat(chapter.getVolumeId()).isEqualTo(900011L);
            assertThat(chapter.getTitle()).isEqualTo("第一章");
        });
        verify(storyBibleApplicationService).bootstrap(900001L, created.getTitle(), 920001L);
    }

    @Test
    void UT_APP_NOVEL_CREATE_PROJECT_INSERT_FAILED() {
        when(businessIdGenerator.nextId()).thenReturn(900002L);
        when(novelGateway.insertProject(any(NovelProject.class))).thenReturn(0);

        assertThatThrownBy(() -> novelApplicationService.createProject(
                new CreateProjectCommand(920001L, "新建项目", "摘要", 2),
                "trace-create-project-failed"
        ))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Failed to create project");
        verifyNoInteractions(storyBibleApplicationService);
    }

    @Test
    void imported_project_persists_the_adjusted_directory_in_one_application_use_case() {
        when(businessIdGenerator.nextId()).thenReturn(900001L, 900011L, 900021L, 900022L);
        when(novelGateway.insertProject(any(NovelProject.class))).thenReturn(1);
        when(novelGateway.insertVolume(any(NovelVolume.class))).thenReturn(1);
        when(novelGateway.insertChapter(any(NovelChapter.class))).thenReturn(1);

        NovelProject created = novelApplicationService.createImportedProject(new ImportProjectCommand(
                new CreateProjectCommand(920001L, "导入作品", null, "其他", null, List.of(), 1),
                List.of(new ImportVolumeCommand("第一卷 雨夜", List.of(
                        new ImportChapterCommand("第一章 来客", "雨水落下。"),
                        new ImportChapterCommand("第二章 回声", "城门合拢。")
                )))
        ), "trace-import");

        assertThat(created.getProjectId()).isEqualTo(900001L);
        ArgumentCaptor<NovelChapter> chapters = ArgumentCaptor.forClass(NovelChapter.class);
        verify(novelGateway, times(2)).insertChapter(chapters.capture());
        assertThat(chapters.getAllValues()).extracting(NovelChapter::getTitle)
                .containsExactly("第一章 来客", "第二章 回声");
        assertThat(chapters.getAllValues()).extracting(NovelChapter::getSortOrder)
                .containsExactly(1, 2);
        assertThat(chapters.getAllValues().getFirst().getWordCount()).isEqualTo(5);
        verify(storyBibleApplicationService).bootstrap(900001L, "导入作品", 920001L);
        verify(projectAiConfigurationService).initializeProject(900001L, 920001L);
    }

    @Test
    void moving_a_project_to_trash_is_scoped_to_the_authenticated_owner() {
        when(novelGateway.softDeleteProject(900002L, 920001L)).thenReturn(1);

        novelApplicationService.deleteProject(900002L, 920001L, "trace-trash");

        verify(novelGateway).softDeleteProject(900002L, 920001L);
    }

    @Test
    void acquires_an_ai_only_lease_with_the_latest_chapter_content() {
        NovelProject project = new NovelProject();
        project.setProjectId(920002L);
        project.setOwnerUserId(920001L);
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        chapter.setContent("第一段");
        chapter.setContentRevision(3L);
        when(novelGateway.findProjectById(920002L)).thenReturn(project);
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);
        when(novelGateway.findChaptersByProjectId(920002L)).thenReturn(List.of(chapter));
        when(novelGateway.acquireChapterAiLease(eq(920002L), eq(920101L), eq(940001L),
                any(String.class), any())).thenReturn(1);

        NovelApplicationService.AiChapterLeaseView lease = novelApplicationService.acquireChapterAiLease(
                920002L, 920101L, 920001L, 940001L);

        assertThat(lease.editable()).isTrue();
        assertThat(lease.leaseToken()).isNotBlank();
        assertThat(lease.content()).isEqualTo("第一段");
        assertThat(lease.contentRevision()).isEqualTo(3L);
    }

    @Test
    void saves_user_content_with_revision_only() {
        NovelProject project = new NovelProject();
        project.setProjectId(920002L);
        project.setOwnerUserId(920001L);
        NovelChapter saved = new NovelChapter();
        saved.setProjectId(920002L);
        saved.setChapterId(920101L);
        saved.setContent("山河 无恙");
        saved.setContentRevision(5L);
        when(novelGateway.findProjectById(920002L)).thenReturn(project);
        when(novelGateway.updateUserChapterContent(920002L, 920101L, 4L, "山河 无恙", 4)).thenReturn(1);
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(saved);
        when(novelGateway.findChaptersByProjectId(920002L)).thenReturn(List.of(saved));

        NovelChapter result = novelApplicationService.saveChapterContent(
                920002L, 920101L, 920001L, 4L, "山河 无恙");

        assertThat(result.getContentRevision()).isEqualTo(5L);
        verify(novelGateway).updateUserChapterContent(920002L, 920101L, 4L, "山河 无恙", 4);
        verify(novelGateway).invalidateAvailableAiUndoByChapter(920002L, 920101L);
    }

    @Test
    void should_save_ai_content_and_undo_operation_as_one_application_transaction() {
        NovelProject project = new NovelProject();
        project.setProjectId(920002L);
        project.setOwnerUserId(920001L);
        NovelChapter current = new NovelChapter();
        current.setProjectId(920002L);
        current.setChapterId(920101L);
        current.setTitle("第一章");
        current.setContent("原正文");
        current.setWordCount(3);
        current.setContentRevision(3L);
        NovelChapter saved = new NovelChapter();
        saved.setProjectId(920002L);
        saved.setChapterId(920101L);
        saved.setTitle("第一章");
        saved.setContent("AI 修改后的正文");
        saved.setWordCount(8);
        saved.setContentRevision(4L);
        when(novelGateway.findProjectById(920002L)).thenReturn(project);
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(current, saved);
        when(novelGateway.updateAiChapterContent(920002L, 920101L, "ai-lease", 3L, "AI 修改后的正文", 8)).thenReturn(1);
        when(novelGateway.findAvailableAiUndoByRunAndChapter(920002L, 940001L, 920101L)).thenReturn(null);
        when(novelGateway.nextAiUndoSequence(920002L, 920101L)).thenReturn(1L);
        when(businessIdGenerator.nextId()).thenReturn(950001L);
        when(novelGateway.insertAiUndo(any())).thenReturn(1);
        when(novelGateway.findChaptersByProjectId(920002L)).thenReturn(List.of(saved));

        NovelApplicationService.AiChapterEditResult result = novelApplicationService.saveAiChapterEdit(
                920002L, 920101L, 920001L, 940001L, "tool-call-1", "ai-lease", 3L,
                "AI 修改后的正文");

        assertThat(result.chapter().getContentRevision()).isEqualTo(4L);
        assertThat(result.undo().operationId()).isEqualTo(950001L);
        ArgumentCaptor<ChapterAiUndoOperation> operation = ArgumentCaptor.forClass(ChapterAiUndoOperation.class);
        verify(novelGateway).insertAiUndo(operation.capture());
        assertThat(operation.getValue()).satisfies(value -> {
            assertThat(value.getBeforeContent()).isEqualTo("原正文");
            assertThat(value.getAppliedRevision()).isEqualTo(4L);
            assertThat(value.getStatus()).isEqualTo("AVAILABLE");
        });
    }

    @Test
    void should_undo_only_the_latest_unchanged_ai_result() throws Exception {
        NovelProject project = new NovelProject();
        project.setProjectId(920002L);
        project.setOwnerUserId(920001L);
        NovelChapter current = new NovelChapter();
        current.setProjectId(920002L);
        current.setChapterId(920101L);
        current.setTitle("第一章");
        current.setContent("AI 正文");
        current.setContentRevision(5L);
        ChapterAiUndoOperation operation = new ChapterAiUndoOperation();
        operation.setOperationId(950001L);
        operation.setProjectId(920002L);
        operation.setChapterId(920101L);
        operation.setRunId(940001L);
        operation.setBeforeContent("原正文");
        operation.setBeforeWordCount(3);
        operation.setResultContentHash(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("AI 正文".getBytes(StandardCharsets.UTF_8))));
        operation.setAppliedRevision(5L);
        operation.setSequenceNo(2L);
        operation.setStatus("AVAILABLE");
        operation.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
        when(novelGateway.findProjectById(920002L)).thenReturn(project);
        when(novelGateway.findAiUndoByOperationId(920002L, 950001L)).thenReturn(operation);
        when(novelGateway.listAvailableAiUndoByChapter(920002L, 920101L))
                .thenReturn(List.of(operation), List.of());
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(current);
        when(novelGateway.restoreAiChapterContent(920002L, 920101L, 5L, "AI 正文", "原正文", 3)).thenReturn(1);
        when(novelGateway.markAiUndoUndone(950001L)).thenReturn(1);

        NovelApplicationService.AiUndoView result = novelApplicationService.undoAiChapterEdit(
                920002L, 950001L, 920001L);

        assertThat(result.status()).isEqualTo("UNDONE");
        verify(novelGateway).restoreAiChapterContent(920002L, 920101L, 5L, "AI 正文", "原正文", 3);
    }

    @Test
    void dismissing_an_undo_also_dismisses_older_entries_in_the_same_chapter_stack() {
        NovelProject project = new NovelProject();
        project.setProjectId(920002L);
        project.setOwnerUserId(920001L);
        ChapterAiUndoOperation older = availableUndo(950001L, 920101L, 1L);
        ChapterAiUndoOperation selected = availableUndo(950002L, 920101L, 2L);
        when(novelGateway.findProjectById(920002L)).thenReturn(project);
        when(novelGateway.findAiUndoByOperationId(920002L, 950002L)).thenReturn(selected);
        when(novelGateway.listAvailableAiUndoByChapter(920002L, 920101L))
                .thenReturn(List.of(selected, older));
        when(novelGateway.dismissAvailableAiUndoThrough(920002L, 920101L, 2L)).thenReturn(2);

        NovelApplicationService.AiUndoDismissResult result = novelApplicationService.dismissAiUndo(
                920002L, List.of(950002L), 920001L);

        assertThat(result.operationIds()).containsExactly(950002L, 950001L);
        verify(novelGateway).dismissAvailableAiUndoThrough(920002L, 920101L, 2L);
    }

    private ChapterAiUndoOperation availableUndo(Long operationId, Long chapterId, Long sequenceNo) {
        ChapterAiUndoOperation operation = new ChapterAiUndoOperation();
        operation.setOperationId(operationId);
        operation.setProjectId(920002L);
        operation.setChapterId(chapterId);
        operation.setRunId(940001L + sequenceNo);
        operation.setSequenceNo(sequenceNo);
        operation.setStatus("AVAILABLE");
        operation.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
        return operation;
    }

    @Test
    void rejects_a_stale_user_revision_with_a_specific_conflict_code() {
        NovelProject project = new NovelProject();
        project.setProjectId(920002L);
        project.setOwnerUserId(920001L);
        NovelChapter current = new NovelChapter();
        current.setProjectId(920002L);
        current.setChapterId(920101L);
        current.setContentRevision(3L);
        when(novelGateway.findProjectById(920002L)).thenReturn(project);
        when(novelGateway.updateUserChapterContent(any(), any(), any(), any(), any())).thenReturn(0);
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(current);
        when(novelGateway.findChaptersByProjectId(920002L)).thenReturn(List.of(current));

        assertThatThrownBy(() -> novelApplicationService.saveChapterContent(
                920002L, 920101L, 920001L, 2L, "正文"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Chapter was updated in another page")
                .satisfies(error -> assertThat(((com.penmate.backend.application.common.exception.BusinessException) error)
                        .getErrorCode()).isEqualTo("CHAPTER_REVISION_CONFLICT"));
    }

    @Test
    void rejects_user_saves_while_ai_owns_the_chapter() {
        NovelProject project = new NovelProject();
        project.setProjectId(920002L);
        project.setOwnerUserId(920001L);
        NovelChapter current = new NovelChapter();
        current.setProjectId(920002L);
        current.setChapterId(920101L);
        current.setContentRevision(3L);
        current.setLeaseOwnerType("AI");
        current.setLeaseExpiresAt(java.time.Instant.now().plusSeconds(60));
        when(novelGateway.findProjectById(920002L)).thenReturn(project);
        when(novelGateway.updateUserChapterContent(any(), any(), any(), any(), any())).thenReturn(0);
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(current);
        when(novelGateway.findChaptersByProjectId(920002L)).thenReturn(List.of(current));

        assertThatThrownBy(() -> novelApplicationService.saveChapterContent(
                920002L, 920101L, 920001L, 3L, "正文"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("AI is editing this chapter")
                .satisfies(error -> assertThat(((com.penmate.backend.application.common.exception.BusinessException) error)
                        .getErrorCode()).isEqualTo("CHAPTER_AI_EDITING"));
    }

    @Test
    void UT_APP_NOVEL_GET_CHAPTER_CONTENT_TEXT_SUCCESS() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        chapter.setContent("夜雨中的追踪在巷口停住。");
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);

        assertThat(novelApplicationService.getChapterContentText(920002L, 920101L))
                .isEqualTo("夜雨中的追踪在巷口停住。");
    }

    @Test
    void UT_APP_NOVEL_UPDATE_PROJECT_SUCCESS_RETURNS_REFRESHED_PROJECT() {
        NovelProject existing = new NovelProject();
        existing.setId(11L);
        existing.setProjectId(920002L);
        existing.setTitle("旧标题");
        existing.setSummary("旧摘要");
        existing.setStatus(2);

        NovelProject refreshed = new NovelProject();
        refreshed.setId(11L);
        refreshed.setProjectId(920002L);
        refreshed.setTitle("新标题");
        refreshed.setSummary("新摘要");
        refreshed.setStatus(2);

        when(novelGateway.findProjectById(920002L)).thenReturn(existing, refreshed);
        when(novelGateway.updateProject(existing)).thenReturn(1);

        NovelProject result = novelApplicationService.updateProject(
                920002L,
                new com.penmate.backend.application.novel.command.NovelCommands.UpdateProjectCommand("新标题", "新摘要", null),
                "trace-update-project"
        );

        assertThat(result).isSameAs(refreshed);
        assertThat(existing.getTitle()).isEqualTo("新标题");
        assertThat(existing.getSummary()).isEqualTo("新摘要");
        assertThat(existing.getStatus()).isEqualTo(2);
        verify(novelGateway).updateProject(existing);
    }

    @Test
    void should_assign_continuous_full_book_display_numbers_from_gateway_order() {
        NovelChapter first = new NovelChapter();
        first.setChapterId(1001L);
        first.setSortOrder(20);
        NovelChapter second = new NovelChapter();
        second.setChapterId(1002L);
        second.setSortOrder(1);
        when(novelGateway.findChaptersByProjectId(920002L)).thenReturn(List.of(first, second));

        List<NovelChapter> chapters = novelApplicationService.listChapters(920002L);

        assertThat(chapters).extracting(NovelChapter::getDisplayNo).containsExactly(1, 2);
    }

    @Test
    void should_move_chapter_and_increment_structure_revision_once() {
        NovelProject project = new NovelProject();
        project.setProjectId(920002L);
        project.setOwnerUserId(920001L);
        project.setStructureRevision(1L);
        NovelChapter first = new NovelChapter();
        first.setChapterId(1000L);
        first.setProjectId(920002L);
        first.setVolumeId(930101L);
        first.setSortOrder(1);
        NovelChapter chapter = new NovelChapter();
        chapter.setChapterId(1001L);
        chapter.setProjectId(920002L);
        chapter.setVolumeId(930101L);
        chapter.setSortOrder(2);
        NovelVolume volume = new NovelVolume();
        volume.setVolumeId(930101L);
        volume.setProjectId(920002L);
        volume.setSortOrder(1);
        when(novelGateway.lockProject(920002L)).thenReturn(project);
        when(novelGateway.findVolumesByProjectId(920002L)).thenReturn(List.of(volume));
        when(novelGateway.findChaptersByProjectId(920002L)).thenReturn(List.of(first, chapter));
        when(novelGateway.updateChapter(any())).thenReturn(1);
        when(novelGateway.incrementStructureRevision(920002L)).thenReturn(1);

        NovelApplicationService.NovelDirectoryView moved = novelApplicationService.moveDirectoryItem(
                920002L,
                new com.penmate.backend.application.novel.command.NovelCommands.MoveDirectoryItemCommand(
                        com.penmate.backend.application.novel.command.NovelCommands.DirectoryNodeType.CHAPTER,
                        1001L, 930101L, 1, 1L),
                920001L,
                "trace-move-directory"
        );

        assertThat(moved.structureRevision()).isEqualTo(2L);
        assertThat(chapter.getSortOrder()).isEqualTo(1);
        assertThat(first.getSortOrder()).isEqualTo(2);
        verify(novelGateway).incrementStructureRevision(920002L);
    }

    @Test
    void should_reject_directory_move_when_structure_revision_is_stale() {
        NovelProject project = new NovelProject();
        project.setProjectId(920002L);
        project.setOwnerUserId(920001L);
        project.setStructureRevision(3L);
        when(novelGateway.lockProject(920002L)).thenReturn(project);

        assertThatThrownBy(() -> novelApplicationService.moveDirectoryItem(
                920002L,
                new com.penmate.backend.application.novel.command.NovelCommands.MoveDirectoryItemCommand(
                        com.penmate.backend.application.novel.command.NovelCommands.DirectoryNodeType.VOLUME,
                        930101L, null, 1, 2L),
                920001L,
                "trace-stale-directory"
        )).hasMessageContaining("directory changed");

        verify(novelGateway, org.mockito.Mockito.never()).updateVolume(org.mockito.ArgumentMatchers.any());
        verify(novelGateway, org.mockito.Mockito.never()).incrementStructureRevision(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void UT_APP_NOVEL_CREATE_VOLUME_DEFAULT_SORT_ORDER_SUCCESS() {
        when(businessIdGenerator.nextId()).thenReturn(930001L);
        when(novelGateway.insertVolume(any())).thenReturn(1);
        when(novelGateway.incrementStructureRevision(920002L)).thenReturn(1);

        NovelVolume volume = novelApplicationService.createVolume(
                920002L,
                new com.penmate.backend.application.novel.command.NovelCommands.CreateVolumeCommand("第一卷", null, "卷描述"),
                920001L,
                "trace-create-volume"
        );

        assertThat(volume.getVolumeId()).isEqualTo(930001L);
        assertThat(volume.getProjectId()).isEqualTo(920002L);
        assertThat(volume.getSortOrder()).isEqualTo(0);
        verify(novelGateway).incrementStructureRevision(920002L);
        assertThat(volume.getTitle()).isEqualTo("第一卷");
    }

    @Test
    void UT_APP_NOVEL_UPDATE_VOLUME_SUCCESS_RETURNS_MATCHED_VOLUME_FROM_LIST() {
        NovelVolume updated = new NovelVolume();
        updated.setVolumeId(930101L);
        updated.setProjectId(920002L);
        updated.setTitle("第一卷-修订");
        updated.setSortOrder(0);

        when(novelGateway.updateVolume(any())).thenReturn(1);
        when(novelGateway.findVolumesByProjectId(920002L)).thenReturn(List.of(updated));

        NovelVolume result = novelApplicationService.updateVolume(
                920002L,
                930101L,
                new com.penmate.backend.application.novel.command.NovelCommands.UpdateVolumeCommand("第一卷-修订", null, "新描述"),
                920001L,
                "trace-update-volume"
        );

        assertThat(result).isSameAs(updated);
        assertThat(result.getSortOrder()).isEqualTo(1);
    }

    @Test
    void UT_APP_NOVEL_DELETE_VOLUME_NOT_FOUND() {
        when(novelGateway.softDeleteVolume(920002L, 930101L)).thenReturn(0);

        assertThatThrownBy(() -> novelApplicationService.deleteVolume(920002L, 930101L, 920001L, "trace-delete-volume"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Volume not found or already deleted");
    }

    @Test
    void should_soft_delete_chapters_before_deleting_volume() {
        when(novelGateway.softDeleteChaptersByVolume(920002L, 930101L)).thenReturn(3);
        when(novelGateway.softDeleteVolume(920002L, 930101L)).thenReturn(1);
        when(novelGateway.incrementStructureRevision(920002L)).thenReturn(1);

        novelApplicationService.deleteVolume(920002L, 930101L, 920001L, "trace-delete-volume");

        var ordered = org.mockito.Mockito.inOrder(novelGateway);
        ordered.verify(novelGateway).softDeleteChaptersByVolume(920002L, 930101L);
        ordered.verify(novelGateway).softDeleteVolume(920002L, 930101L);
        ordered.verify(novelGateway).incrementStructureRevision(920002L);
    }

}
