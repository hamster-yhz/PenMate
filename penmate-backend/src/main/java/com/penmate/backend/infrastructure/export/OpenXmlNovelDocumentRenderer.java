package com.penmate.backend.infrastructure.export;

import com.penmate.backend.application.novel.export.NovelDocumentRenderer;
import com.penmate.backend.application.novel.export.NovelExportFormat;
import com.penmate.backend.application.novel.export.NovelManuscript;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;

@Component
public class OpenXmlNovelDocumentRenderer implements NovelDocumentRenderer {
    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Override
    public byte[] render(NovelExportFormat format, NovelManuscript manuscript) {
        return switch (format) {
            case TXT -> withBom(renderText(manuscript).getBytes(StandardCharsets.UTF_8));
            case MARKDOWN -> withBom(renderMarkdown(manuscript).getBytes(StandardCharsets.UTF_8));
            case DOCX -> renderDocx(manuscript);
            case EPUB -> EpubNovelDocumentRenderer.render(manuscript);
        };
    }

    private String renderText(NovelManuscript manuscript) {
        StringBuilder text = new StringBuilder(manuscript.title()).append("\n\n");
        for (NovelManuscript.Volume volume : manuscript.volumes()) {
            text.append(volume.title()).append("\n\n");
            for (NovelManuscript.Chapter chapter : volume.chapters()) {
                text.append(chapter.title()).append("\n\n");
                if (!chapter.content().isEmpty()) text.append(normalizeNewlines(chapter.content())).append('\n');
                text.append('\n');
            }
        }
        return text.toString();
    }

    private String renderMarkdown(NovelManuscript manuscript) {
        StringBuilder markdown = new StringBuilder("# ").append(escapeHeading(manuscript.title())).append("\n\n");
        for (NovelManuscript.Volume volume : manuscript.volumes()) {
            markdown.append("## ").append(escapeHeading(volume.title())).append("\n\n");
            for (NovelManuscript.Chapter chapter : volume.chapters()) {
                markdown.append("### ").append(escapeHeading(chapter.title())).append("\n\n");
                if (!chapter.content().isEmpty()) markdown.append(normalizeNewlines(chapter.content())).append('\n');
                markdown.append('\n');
            }
        }
        return markdown.toString();
    }

    private byte[] renderDocx(NovelManuscript manuscript) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            defineStyles(document);
            XWPFParagraph title = document.createParagraph();
            title.setStyle("Title");
            title.setAlignment(ParagraphAlignment.CENTER);
            title.createRun().setText(manuscript.title());
            for (NovelManuscript.Volume volume : manuscript.volumes()) {
                paragraph(document, volume.title(), "Heading1");
                for (NovelManuscript.Chapter chapter : volume.chapters()) {
                    paragraph(document, chapter.title(), "Heading2");
                    for (String line : normalizeNewlines(chapter.content()).split("\n", -1)) {
                        paragraph(document, line, null);
                    }
                }
            }
            document.getProperties().getCoreProperties().setTitle(manuscript.title());
            document.getProperties().getExtendedProperties().getUnderlyingProperties().setApplication("PenMate");
            document.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to render DOCX export", exception);
        }
    }

    private void paragraph(XWPFDocument document, String value, String style) {
        XWPFParagraph paragraph = document.createParagraph();
        if (style != null) paragraph.setStyle(style);
        if (value != null && !value.isEmpty()) paragraph.createRun().setText(value);
    }

    private void defineStyles(XWPFDocument document) {
        XWPFStyles styles = document.createStyles();
        styles.addStyle(style("Title", "Title", null));
        styles.addStyle(style("Heading1", "heading 1", 0));
        styles.addStyle(style("Heading2", "heading 2", 1));
    }

    private XWPFStyle style(String id, String name, Integer outlineLevel) {
        CTStyle value = CTStyle.Factory.newInstance();
        value.setStyleId(id);
        value.setType(STStyleType.PARAGRAPH);
        value.addNewName().setVal(name);
        value.addNewQFormat();
        if (outlineLevel != null) value.addNewPPr().addNewOutlineLvl().setVal(BigInteger.valueOf(outlineLevel));
        return new XWPFStyle(value);
    }

    private byte[] withBom(byte[] body) {
        byte[] result = new byte[UTF_8_BOM.length + body.length];
        System.arraycopy(UTF_8_BOM, 0, result, 0, UTF_8_BOM.length);
        System.arraycopy(body, 0, result, UTF_8_BOM.length, body.length);
        return result;
    }

    private String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String escapeHeading(String value) {
        return value.replace("\\", "\\\\").replace("#", "\\#");
    }
}
