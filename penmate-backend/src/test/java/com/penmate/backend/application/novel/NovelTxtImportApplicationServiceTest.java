package com.penmate.backend.application.novel;

import com.penmate.backend.application.novel.command.NovelCommands.ImportProjectCommand;
import com.penmate.backend.domain.novel.model.NovelProject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NovelTxtImportApplicationServiceTest {

    private final NovelApplicationService novelApplicationService = mock(NovelApplicationService.class);
    private final NovelTxtImportApplicationService service = new NovelTxtImportApplicationService(novelApplicationService);

    @Test
    void splits_utf8_txt_into_editable_volume_and_chapter_preview() {
        String text = """
                第一卷 雨夜
                第一章 来客
                雨水沿着檐角落下。

                第二章 回声
                城门在身后合拢。
                第二卷 旧城
                第三章 密信
                信纸上没有署名。
                """;

        var preview = service.preview("长夜.txt", text.getBytes(StandardCharsets.UTF_8));

        assertThat(preview.projectTitle()).isEqualTo("长夜");
        assertThat(preview.volumes()).hasSize(2);
        assertThat(preview.volumes().getFirst().title()).isEqualTo("第一卷 雨夜");
        assertThat(preview.volumes().getFirst().chapters()).extracting(
                NovelTxtImportApplicationService.ImportChapterPreview::title)
                .containsExactly("第一章 来客", "第二章 回声");
        assertThat(preview.volumes().getFirst().chapters().getFirst().content())
                .isEqualTo("雨水沿着檐角落下。");
    }

    @Test
    void creates_a_default_volume_and_chapter_for_plain_text_without_headings() {
        var preview = service.preview("散文.txt", "只有正文。".getBytes(StandardCharsets.UTF_8));

        assertThat(preview.volumes()).singleElement().satisfies(volume -> {
            assertThat(volume.title()).isEqualTo("第一卷");
            assertThat(volume.chapters()).singleElement().satisfies(chapter -> {
                assertThat(chapter.title()).isEqualTo("第一章");
                assertThat(chapter.content()).isEqualTo("只有正文。");
            });
        });
    }

    @Test
    void rejects_non_utf8_input() {
        assertThatThrownBy(() -> service.preview("bad.txt", new byte[]{(byte) 0xC3, (byte) 0x28}))
                .hasMessage("TXT file must use UTF-8 encoding");
    }

    @Test
    void maps_the_adjusted_preview_to_the_transactional_import_command() {
        var preview = service.preview("原名.txt", "第一章 原标题\n正文".getBytes(StandardCharsets.UTF_8));
        var adjusted = new NovelTxtImportApplicationService.ImportPreview("调整后作品名", preview.volumes());
        NovelProject created = new NovelProject();
        created.setProjectId(3001L);
        when(novelApplicationService.createImportedProject(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("trace")))
                .thenReturn(created);

        assertThat(service.importProject(1001L, adjusted, "trace").getProjectId()).isEqualTo(3001L);
        ArgumentCaptor<ImportProjectCommand> captor = ArgumentCaptor.forClass(ImportProjectCommand.class);
        verify(novelApplicationService).createImportedProject(captor.capture(), org.mockito.ArgumentMatchers.eq("trace"));
        assertThat(captor.getValue().project().ownerUserId()).isEqualTo(1001L);
        assertThat(captor.getValue().project().title()).isEqualTo("调整后作品名");
        assertThat(captor.getValue().volumes().getFirst().chapters().getFirst().content()).isEqualTo("正文");
    }
}
