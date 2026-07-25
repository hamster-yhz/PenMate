package com.penmate.backend.infrastructure.importing;

import com.penmate.backend.application.novel.export.NovelExportFormat;
import com.penmate.backend.application.novel.export.NovelManuscript;
import com.penmate.backend.infrastructure.export.OpenXmlNovelDocumentRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NovelImportSourceParserTest {
    @Test
    void parses_txt_without_creating_a_chapter_from_leading_blank_lines() throws Exception {
        String text = "长夜\n\n第一卷 雨夜\n\n第一章 来客\n\n雨水落下。\n";
        var draft = new TxtNovelImportSourceParser().parse("长夜.txt",
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));

        assertThat(draft.volumes()).hasSize(1);
        assertThat(draft.volumes().getFirst().chapters()).singleElement().satisfies(chapter -> {
            assertThat(chapter.title()).isEqualTo("第一章 来客");
            assertThat(chapter.content()).isEqualTo("雨水落下。");
        });
    }

    @Test
    void exported_txt_is_safe_to_import_again() throws Exception {
        var manuscript = new NovelManuscript("长夜", List.of(new NovelManuscript.Volume("第一卷", List.of(
                new NovelManuscript.Chapter("第一章", "第一段。\n\n第二段。")))));
        byte[] exported = new OpenXmlNovelDocumentRenderer().render(NovelExportFormat.TXT, manuscript);

        var draft = new TxtNovelImportSourceParser().parse("长夜.txt", new ByteArrayInputStream(exported));

        assertThat(draft.chapterCount()).isEqualTo(1);
        assertThat(draft.volumes().getFirst().chapters().getFirst().content()).isEqualTo("第一段。\n\n第二段。");
    }

    @Test
    void parses_markdown_title_volume_and_chapter_hierarchy() throws Exception {
        String markdown = "# 长夜\n\n## 第一卷\n\n### 第一章\n\n正文。";
        var draft = new MarkdownNovelImportSourceParser().parse("draft.md",
                new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));

        assertThat(draft.projectTitle()).isEqualTo("长夜");
        assertThat(draft.volumes().getFirst().title()).isEqualTo("第一卷");
        assertThat(draft.volumes().getFirst().chapters().getFirst().title()).isEqualTo("第一章");
    }

    @Test
    void parses_docx_heading_styles() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var title = document.createParagraph(); title.setStyle("Title"); title.createRun().setText("长夜");
            var volume = document.createParagraph(); volume.setStyle("Heading1"); volume.createRun().setText("第一卷");
            var chapter = document.createParagraph(); chapter.setStyle("Heading2"); chapter.createRun().setText("第一章");
            document.createParagraph().createRun().setText("正文。");
            document.write(output); bytes = output.toByteArray();
        }
        var draft = new DocxNovelImportSourceParser().parse("draft.docx", new ByteArrayInputStream(bytes));
        assertThat(draft.projectTitle()).isEqualTo("长夜");
        assertThat(draft.chapterCount()).isEqualTo(1);
        assertThat(draft.volumes().getFirst().chapters().getFirst().content()).isEqualTo("正文。");
    }
}
