package com.penmate.backend.application.novel.export;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NovelExportServiceTest {

    private final NovelGateway novelGateway = mock(NovelGateway.class);
    private final NovelDocumentRenderer renderer = mock(NovelDocumentRenderer.class);
    private final NovelExportService service = new NovelExportService(novelGateway, renderer);

    @Test
    void exports_owned_project_in_volume_and_chapter_order() {
        NovelProject project = project(2001L, 1001L, "My:Novel");
        NovelVolume laterVolume = volume(22L, "Volume Two", 2);
        NovelVolume firstVolume = volume(11L, "Volume One", 1);
        NovelChapter laterChapter = chapter(202L, 11L, "Chapter Two", 2, "second");
        NovelChapter firstChapter = chapter(101L, 11L, "Chapter One", 1, "first");
        NovelChapter otherVolumeChapter = chapter(301L, 22L, "Chapter Three", 1, "third");

        when(novelGateway.findProjectById(2001L)).thenReturn(project);
        when(novelGateway.findVolumesByProjectId(2001L)).thenReturn(List.of(laterVolume, firstVolume));
        when(novelGateway.findChaptersByProjectId(2001L))
                .thenReturn(List.of(laterChapter, otherVolumeChapter, firstChapter));
        when(renderer.render(eq(NovelExportFormat.DOCX), any())).thenReturn("docx".getBytes(StandardCharsets.UTF_8));

        NovelExportService.ExportedNovel result = service.export(2001L, 1001L, "docx");

        assertThat(result.fileName()).isEqualTo("My_Novel.docx");
        assertThat(result.contentType()).contains("wordprocessingml");
        assertThat(result.content()).isEqualTo("docx".getBytes(StandardCharsets.UTF_8));
        ArgumentCaptor<NovelManuscript> manuscriptCaptor = ArgumentCaptor.forClass(NovelManuscript.class);
        verify(renderer).render(eq(NovelExportFormat.DOCX), manuscriptCaptor.capture());
        NovelManuscript manuscript = manuscriptCaptor.getValue();
        assertThat(manuscript.volumes()).extracting(NovelManuscript.Volume::title)
                .containsExactly("Volume One", "Volume Two");
        assertThat(manuscript.volumes().getFirst().chapters()).extracting(NovelManuscript.Chapter::title)
                .containsExactly("Chapter One", "Chapter Two");
    }

    @Test
    void hides_project_existence_from_non_owner() {
        when(novelGateway.findProjectById(2001L)).thenReturn(project(2001L, 1001L, "Private novel"));

        assertThatThrownBy(() -> service.export(2001L, 9999L, "txt"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Novel project not found");
        verifyNoInteractions(renderer);
    }

    @Test
    void rejects_unknown_format_before_rendering() {
        when(novelGateway.findProjectById(2001L)).thenReturn(project(2001L, 1001L, "Novel"));

        assertThatThrownBy(() -> service.export(2001L, 1001L, "pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("[txt, docx]");
        verifyNoInteractions(renderer);
    }

    private NovelProject project(Long projectId, Long ownerId, String title) {
        NovelProject project = new NovelProject();
        project.setProjectId(projectId);
        project.setOwnerUserId(ownerId);
        project.setTitle(title);
        return project;
    }

    private NovelVolume volume(Long volumeId, String title, int sortOrder) {
        NovelVolume volume = new NovelVolume();
        volume.setVolumeId(volumeId);
        volume.setTitle(title);
        volume.setSortOrder(sortOrder);
        return volume;
    }

    private NovelChapter chapter(Long chapterId, Long volumeId, String title, int sortOrder, String content) {
        NovelChapter chapter = new NovelChapter();
        chapter.setChapterId(chapterId);
        chapter.setVolumeId(volumeId);
        chapter.setTitle(title);
        chapter.setSortOrder(sortOrder);
        chapter.setContent(content);
        return chapter;
    }
}
