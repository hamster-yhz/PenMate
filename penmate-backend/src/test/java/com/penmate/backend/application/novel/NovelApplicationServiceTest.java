package com.penmate.backend.application.novel;

import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.application.novel.command.NovelCommands.CommitChapterContentCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateOutlineNodeCommand;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.application.rag.ProjectAiConfigurationService;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelChapterVersion;
import com.penmate.backend.domain.novel.model.NovelOutlineNode;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private NovelGateway novelGateway;

    @Mock
    private RealtimeEventService realtimeEventService;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

    @Mock
    private ObjectStorageService objectStorageService;

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
    void UT_APP_NOVEL_CREATE_OUTLINE_NODE_ALLOW_NULL_PARENT_ID() {
        NovelProject project = new NovelProject();
        project.setId(920002L);
        project.setTitle("DBCASE_长夜行_连载");
        when(businessIdGenerator.nextId()).thenReturn(930001L);
        when(novelGateway.findProjectById(920002L)).thenReturn(project);
        when(novelGateway.insertOutlineNode(any(NovelOutlineNode.class))).thenAnswer(invocation -> {
            NovelOutlineNode node = invocation.getArgument(0);
            node.setId(123L);
            return 1;
        });
        doNothing().when(realtimeEventService).publishProjectEvent(eq(920002L), eq("outline.node.created"), any());

        NovelOutlineNode created = novelApplicationService.createOutlineNode(
                920002L,
                new CreateOutlineNodeCommand(null, "第一卷：新的篇章", "chapter", 1, "content"),
                920001L,
                "trace-test"
        );

        ArgumentCaptor<NovelOutlineNode> nodeCaptor = ArgumentCaptor.forClass(NovelOutlineNode.class);
        verify(novelGateway).insertOutlineNode(nodeCaptor.capture());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass((Class) Map.class);
        verify(realtimeEventService).publishProjectEvent(eq(920002L), eq("outline.node.created"), payloadCaptor.capture());
        assertThat(created.getId()).isEqualTo(123L);
        assertThat(created.getOutlineNodeId()).isEqualTo(930001L);
        assertThat(created.getParentId()).isNull();
        assertThat(nodeCaptor.getValue().getOutlineNodeId()).isEqualTo(930001L);
        assertThat(payloadCaptor.getValue()).containsEntry("nodeId", 930001L);
    }

    @Test
    void UT_APP_NOVEL_CREATE_PROJECT_DEFAULT_STATUS_AND_GENERATED_ID() {
        when(businessIdGenerator.nextId()).thenReturn(900001L);
        when(novelGateway.insertProject(any(NovelProject.class))).thenReturn(1);

        NovelProject created = novelApplicationService.createProject(
                new CreateProjectCommand(920001L, "新建项目", "摘要", null),
                "trace-create-project"
        );

        assertThat(created.getProjectId()).isEqualTo(900001L);
        assertThat(created.getStatus()).isEqualTo(1);
        verify(novelGateway).insertProject(any(NovelProject.class));
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
    void UT_APP_NOVEL_COMMIT_CHAPTER_CONTENT_REJECTS_INLINE_CONTENT() {
        assertThatThrownBy(() -> novelApplicationService.commitChapterContent(
                920002L,
                920101L,
                new CommitChapterContentCommand("novels/1.md", "etag", 12L, "sha256", null, "inline-content"),
                920001L,
                "trace-commit-inline"
        ))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Direct upload mode does not accept content in commit payload");

        verify(novelGateway, never()).updateChapterContentMeta(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void UT_APP_NOVEL_COMMIT_CHAPTER_CONTENT_NULL_COMMAND_SHOULD_FAIL_FAST() {
        assertThatThrownBy(() -> novelApplicationService.commitChapterContent(
                920002L,
                920101L,
                null,
                920001L,
                "trace-commit-null-command"
        ))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");

        verifyNoInteractions(novelGateway);
    }

    @Test
    void UT_APP_NOVEL_GET_CHAPTER_CONTENT_URL_RETURNS_EMPTY_WHEN_OBJECT_KEY_BLANK() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        chapter.setContentObjectKey("  ");
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);

        assertThat(novelApplicationService.getChapterContentUrl(920002L, 920101L))
                .containsEntry("url", "");
    }

    @Test
    void UT_APP_NOVEL_GET_CHAPTER_CONTENT_URL_SUCCESS() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        chapter.setContentObjectKey("novels/920002/chapters/920101/content.md");
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);
        when(objectStorageService.buildReadUrl("novels/920002/chapters/920101/content.md"))
                .thenReturn("https://cdn.local/read-url");

        assertThat(novelApplicationService.getChapterContentUrl(920002L, 920101L))
                .containsEntry("url", "https://cdn.local/read-url");
    }

    @Test
    void UT_APP_NOVEL_GET_CHAPTER_CONTENT_URL_SHOULD_THROW_WHEN_CHAPTER_NOT_FOUND() {
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(null);

        assertThatThrownBy(() -> novelApplicationService.getChapterContentUrl(920002L, 920101L))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Chapter not found");
    }

    @Test
    void UT_APP_NOVEL_GET_CHAPTER_CONTENT_UPLOAD_URL_SUCCESS() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);
        when(objectStorageService.buildUploadUrl(any(), eq("text/plain; charset=utf-8")))
                .thenReturn("https://oss.local/upload-url");

        java.util.Map<String, String> result = novelApplicationService.getChapterContentUploadUrl(920002L, 920101L);

        assertThat(result)
                .containsEntry("uploadUrl", "https://oss.local/upload-url")
                .containsKey("objectKey");
        assertThat(result.get("objectKey"))
                .startsWith("novels/920002/chapters/920101/")
                .endsWith(".md");
    }

    @Test
    void UT_APP_NOVEL_GET_CHAPTER_VERSION_NOT_FOUND() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);
        when(novelGateway.findVersionByChapterAndVersion(920101L, 3)).thenReturn(null);

        assertThatThrownBy(() -> novelApplicationService.getChapterVersion(920002L, 920101L, 3))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Chapter version not found");
    }

    @Test
    void UT_APP_NOVEL_GET_CHAPTER_VERSION_SNAPSHOT_URL_RETURNS_EMPTY_WHEN_OBJECT_KEY_BLANK() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        NovelChapterVersion version = new NovelChapterVersion();
        version.setVersionNo(3);
        version.setSnapshotObjectKey("   ");
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);
        when(novelGateway.findVersionByChapterAndVersion(920101L, 3)).thenReturn(version);

        assertThat(novelApplicationService.getChapterVersionSnapshotUrl(920002L, 920101L, 3))
                .containsEntry("url", "");
    }

    @Test
    void UT_APP_NOVEL_GET_CHAPTER_VERSION_SNAPSHOT_URL_SUCCESS() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        NovelChapterVersion version = new NovelChapterVersion();
        version.setVersionNo(3);
        version.setSnapshotObjectKey("novels/920002/chapters/920101/versions/3/snapshot.md");
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);
        when(novelGateway.findVersionByChapterAndVersion(920101L, 3)).thenReturn(version);
        when(objectStorageService.buildReadUrl("novels/920002/chapters/920101/versions/3/snapshot.md"))
                .thenReturn("https://cdn.local/snapshot-url");

        assertThat(novelApplicationService.getChapterVersionSnapshotUrl(920002L, 920101L, 3))
                .containsEntry("url", "https://cdn.local/snapshot-url");
    }

    @Test
    void UT_APP_NOVEL_GET_CHAPTER_VERSION_SNAPSHOT_URL_SHOULD_THROW_WHEN_VERSION_NOT_FOUND() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);
        when(novelGateway.findVersionByChapterAndVersion(920101L, 3)).thenReturn(null);

        assertThatThrownBy(() -> novelApplicationService.getChapterVersionSnapshotUrl(920002L, 920101L, 3))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Chapter version not found");
    }

    @Test
    void UT_APP_NOVEL_COMMIT_CHAPTER_CONTENT_SHOULD_FAIL_WHEN_UPDATE_COUNT_IS_NOT_ONE() {
        when(novelGateway.updateChapterContentMeta(
                eq(920002L),
                eq(920101L),
                eq("novels/920002/chapters/920101/content.md"),
                eq("etag-1"),
                eq(128L),
                eq("sha-256"),
                eq("s3")
        )).thenReturn(0);

        assertThatThrownBy(() -> novelApplicationService.commitChapterContent(
                920002L,
                920101L,
                new CommitChapterContentCommand("novels/920002/chapters/920101/content.md", "etag-1", 128L, "sha-256", null, null),
                920001L,
                "trace-commit-failed"
        ))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Failed to commit chapter content");
    }

    @Test
    void UT_APP_NOVEL_GET_CHAPTER_CONTENT_TEXT_SUCCESS() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        chapter.setContentObjectKey("novels/920002/chapters/920101/content.md");
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);
        when(objectStorageService.readText("novels/920002/chapters/920101/content.md"))
                .thenReturn("夜雨中的追踪在巷口停住。");

        assertThat(novelApplicationService.getChapterContentText(920002L, 920101L))
                .isEqualTo("夜雨中的追踪在巷口停住。");
    }

    @Test
    void UT_APP_NOVEL_COMMIT_CHAPTER_CONTENT_DEFAULT_STORAGE_PROVIDER() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        when(novelGateway.updateChapterContentMeta(
                eq(920002L),
                eq(920101L),
                eq("novels/920002/chapters/920101/content.md"),
                eq("etag-1"),
                eq(128L),
                eq("sha-256"),
                eq("s3")
        )).thenReturn(1);
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);

        NovelChapter result = novelApplicationService.commitChapterContent(
                920002L,
                920101L,
                new CommitChapterContentCommand("novels/920002/chapters/920101/content.md", "etag-1", 128L, "sha-256", null, null),
                920001L,
                "trace-commit"
        );

        assertThat(result).isSameAs(chapter);
        verify(novelGateway).updateChapterContentMeta(
                920002L,
                920101L,
                "novels/920002/chapters/920101/content.md",
                "etag-1",
                128L,
                "sha-256",
                "s3"
        );
    }

    @Test
    void UT_APP_NOVEL_CREATE_CHAPTER_VERSION_SHOULD_INCREMENT_FROM_MAX_VERSION() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        chapter.setContentObjectKey("novels/920002/chapters/920101/content.md");
        chapter.setContentEtag("etag-latest");
        chapter.setContentSize(1024L);
        chapter.setContentChecksum("sha-latest");
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);
        when(novelGateway.maxVersionNo(920101L)).thenReturn(7);
        when(novelGateway.insertChapterVersion(any(NovelChapterVersion.class))).thenReturn(1);

        NovelChapterVersion created = novelApplicationService.createChapterVersion(
                920002L,
                920101L,
                new com.penmate.backend.application.novel.command.NovelCommands.CreateChapterVersionCommand("manual-save", "保存草稿", 920001L),
                "trace-version-create"
        );

        assertThat(created.getVersionNo()).isEqualTo(8);
        assertThat(created.getSnapshotObjectKey()).isEqualTo("novels/920002/chapters/920101/content.md");
        assertThat(created.getSnapshotEtag()).isEqualTo("etag-latest");
        verify(novelGateway).insertChapterVersion(any(NovelChapterVersion.class));
    }

    @Test
    void UT_APP_NOVEL_RESTORE_CHAPTER_VERSION_SHOULD_FAIL_WHEN_UPDATE_COUNT_IS_NOT_ONE() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        NovelChapterVersion version = new NovelChapterVersion();
        version.setVersionNo(3);
        version.setSnapshotObjectKey("novels/920002/chapters/920101/versions/3/snapshot.md");
        version.setSnapshotEtag("etag-v3");
        version.setSnapshotSize(256L);
        version.setSnapshotChecksum("sha-v3");
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);
        when(novelGateway.findVersionByChapterAndVersion(920101L, 3)).thenReturn(version);
        when(novelGateway.updateChapterContentMeta(
                eq(920002L),
                eq(920101L),
                eq("novels/920002/chapters/920101/versions/3/snapshot.md"),
                eq("etag-v3"),
                eq(256L),
                eq("sha-v3"),
                eq("s3")
        )).thenReturn(0);

        assertThatThrownBy(() -> novelApplicationService.restoreChapterVersion(920002L, 920101L, 3, 920001L, "trace-restore-failed"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Failed to restore chapter version");
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
        NovelChapter chapter = new NovelChapter();
        chapter.setChapterId(1001L);
        chapter.setProjectId(920002L);
        chapter.setSortOrder(2);
        when(novelGateway.findChapterByIdAndProjectId(920002L, 1001L)).thenReturn(chapter);
        when(novelGateway.findChaptersByProjectId(920002L)).thenReturn(List.of(chapter));
        when(novelGateway.updateChapter(chapter)).thenReturn(1);
        when(novelGateway.incrementStructureRevision(920002L)).thenReturn(1);

        NovelChapter moved = novelApplicationService.moveChapter(
                920002L,
                1001L,
                new com.penmate.backend.application.novel.command.NovelCommands.MoveChapterCommand(null, 1),
                920001L,
                "trace-move-chapter"
        );

        assertThat(moved.getSortOrder()).isEqualTo(1);
        verify(novelGateway).incrementStructureRevision(920002L);
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
        assertThat(result.getSortOrder()).isEqualTo(0);
    }

    @Test
    void UT_APP_NOVEL_DELETE_VOLUME_NOT_FOUND() {
        when(novelGateway.softDeleteVolume(920002L, 930101L)).thenReturn(0);

        assertThatThrownBy(() -> novelApplicationService.deleteVolume(920002L, 930101L, 920001L, "trace-delete-volume"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Volume not found or already deleted");
    }

    @Test
    void UT_APP_NOVEL_LIST_CHAPTER_VERSIONS_SUCCESS() {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(920002L);
        chapter.setChapterId(920101L);
        NovelChapterVersion version = new NovelChapterVersion();
        version.setVersionNo(1);
        when(novelGateway.findChapterByIdAndProjectId(920002L, 920101L)).thenReturn(chapter);
        when(novelGateway.findVersionsByChapterId(920101L)).thenReturn(List.of(version));

        List<NovelChapterVersion> result = novelApplicationService.listChapterVersions(920002L, 920101L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVersionNo()).isEqualTo(1);
    }

    @Test
    void UT_APP_NOVEL_LIST_OUTLINE_TREE_SUCCESS() {
        NovelProject project = new NovelProject();
        project.setProjectId(920002L);
        project.setTitle("长夜行");
        NovelOutlineNode node = new NovelOutlineNode();
        node.setId(9001L);
        node.setProjectId(920002L);
        when(novelGateway.findProjectById(920002L)).thenReturn(project);
        when(novelGateway.findOutlineNodesByProjectId(920002L)).thenReturn(List.of(node));

        List<NovelOutlineNode> result = novelApplicationService.listOutlineTree(920002L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(9001L);
    }

}


