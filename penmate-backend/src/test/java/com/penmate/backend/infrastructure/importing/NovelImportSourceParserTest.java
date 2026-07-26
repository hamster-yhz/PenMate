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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void exported_epub_can_be_imported_with_volume_order_and_paragraphs_preserved() throws Exception {
        var manuscript = new NovelManuscript("长夜 & 微光", List.of(
                new NovelManuscript.Volume("上卷", List.of(
                        new NovelManuscript.Chapter("第一章 <来客>", "第一段。\n\n第二段。"),
                        new NovelManuscript.Chapter("第二章", "风停了。")
                )),
                new NovelManuscript.Volume("下卷", List.of(
                        new NovelManuscript.Chapter("第三章", "灯亮了。\n又熄灭了。")
                ))
        ));
        byte[] exported = new OpenXmlNovelDocumentRenderer().render(NovelExportFormat.EPUB, manuscript);

        var draft = new EpubNovelImportSourceParser().parse("fallback.epub", new ByteArrayInputStream(exported));

        assertThat(draft.projectTitle()).isEqualTo("长夜 & 微光");
        assertThat(draft.sourceFormat().name()).isEqualTo("EPUB");
        assertThat(draft.volumes()).extracting(volume -> volume.title()).containsExactly("上卷", "下卷");
        assertThat(draft.volumes().getFirst().chapters()).extracting(chapter -> chapter.title())
                .containsExactly("第一章 <来客>", "第二章");
        assertThat(draft.volumes().getFirst().chapters().getFirst().content())
                .isEqualTo("第一段。\n\n第二段。");
        assertThat(draft.volumes().get(1).chapters().getFirst().content())
                .isEqualTo("灯亮了。\n又熄灭了。");
    }

    @Test
    void rejects_epub_archive_paths_that_escape_the_package() throws Exception {
        byte[] archive;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("mimetype"));
            zip.write("application/epub+zip".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("../outside.xhtml"));
            zip.write("outside".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            archive = output.toByteArray();
        }

        assertThatThrownBy(() -> new EpubNovelImportSourceParser().parse(
                "unsafe.epub", new ByteArrayInputStream(archive)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes the archive");
    }
}
