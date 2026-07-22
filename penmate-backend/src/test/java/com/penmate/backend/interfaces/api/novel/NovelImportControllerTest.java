package com.penmate.backend.interfaces.api.novel;

import com.penmate.backend.application.novel.NovelCoverApplicationService;
import com.penmate.backend.application.novel.NovelTxtImportApplicationService;
import com.penmate.backend.application.novel.NovelTxtImportApplicationService.ImportChapterPreview;
import com.penmate.backend.application.novel.NovelTxtImportApplicationService.ImportPreview;
import com.penmate.backend.application.novel.NovelTxtImportApplicationService.ImportVolumePreview;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.interfaces.api.novel.dto.NovelTxtImportDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NovelImportControllerTest {

    private final NovelTxtImportApplicationService importService = mock(NovelTxtImportApplicationService.class);
    private final NovelCoverApplicationService coverService = mock(NovelCoverApplicationService.class);
    private final NovelImportController controller = new NovelImportController(importService, coverService);

    @Test
    void previews_the_uploaded_txt_without_creating_a_project() {
        byte[] content = "第一章\n正文".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "长夜.txt", "text/plain", content);
        ImportPreview preview = new ImportPreview("长夜", List.of(
                new ImportVolumePreview("第一卷", List.of(new ImportChapterPreview("第一章", "正文")))));
        when(importService.preview("长夜.txt", content)).thenReturn(preview);

        assertThat(controller.preview(file, "trace").getData()).isEqualTo(preview);
        verify(importService).preview("长夜.txt", content);
    }

    @Test
    void imports_the_adjusted_preview_for_the_authenticated_user() {
        NovelTxtImportDto dto = new NovelTxtImportDto();
        dto.setProjectTitle("调整后的长夜");
        NovelTxtImportDto.VolumeDto volume = new NovelTxtImportDto.VolumeDto();
        volume.setTitle("第一卷 雨夜");
        NovelTxtImportDto.ChapterDto chapter = new NovelTxtImportDto.ChapterDto();
        chapter.setTitle("第一章 来客");
        chapter.setContent("雨水落下。");
        volume.setChapters(List.of(chapter));
        dto.setVolumes(List.of(volume));
        NovelProject created = new NovelProject();
        created.setProjectId(2001L);
        when(importService.importProject(org.mockito.ArgumentMatchers.eq(1001L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("trace")))
                .thenReturn(created);
        when(coverService.decorate(created)).thenReturn(created);

        var response = controller.importProject(dto,
                new UsernamePasswordAuthenticationToken("1001", null, List.of()), "trace");

        assertThat(response.getData().getProjectId()).isEqualTo(2001L);
        ArgumentCaptor<ImportPreview> preview = ArgumentCaptor.forClass(ImportPreview.class);
        verify(importService).importProject(org.mockito.ArgumentMatchers.eq(1001L), preview.capture(), org.mockito.ArgumentMatchers.eq("trace"));
        assertThat(preview.getValue().projectTitle()).isEqualTo("调整后的长夜");
        assertThat(preview.getValue().volumes().getFirst().chapters().getFirst().content()).isEqualTo("雨水落下。");
    }
}
