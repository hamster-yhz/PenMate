package com.penmate.backend.infrastructure.importing;

import com.penmate.backend.application.novel.importing.NovelImportSourceParser;
import com.penmate.backend.domain.novel.importing.NovelImportDraft;
import com.penmate.backend.domain.novel.importing.NovelImportFormat;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@Component
public class DocxNovelImportSourceParser implements NovelImportSourceParser {
    @Override public NovelImportFormat format() { return NovelImportFormat.DOCX; }

    @Override
    public NovelImportDraft parse(String filename, InputStream input) throws IOException {
        try (XWPFDocument document = new XWPFDocument(input)) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            boolean hasHeadingTwo = paragraphs.stream().anyMatch(paragraph -> headingLevel(paragraph) >= 2);
            String projectTitle = TxtNovelImportSourceParser.titleFromFilename(filename, ".docx");
            StructuredTextDraftBuilder builder = new StructuredTextDraftBuilder(format());
            boolean titleConsumed = false;
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText() == null ? "" : paragraph.getText();
                int level = headingLevel(paragraph);
                if (isTitle(paragraph) && !titleConsumed) {
                    if (!text.isBlank()) projectTitle = text.strip();
                    titleConsumed = true;
                } else if (hasHeadingTwo && level == 1) {
                    builder.volume(text);
                } else if ((hasHeadingTwo && level >= 2) || (!hasHeadingTwo && level == 1)) {
                    builder.chapter(text);
                } else if (paragraph.isPageBreak() && !text.isBlank()) {
                    builder.chapter(text);
                } else {
                    builder.content(text);
                }
            }
            return builder.build(projectTitle);
        } catch (org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException exception) {
            throw new IllegalArgumentException("Legacy .doc files are not supported; save as .docx first", exception);
        } catch (org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException exception) {
            throw new IllegalArgumentException("The uploaded file is not a valid DOCX document", exception);
        }
    }

    private int headingLevel(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style == null) return 0;
        String normalized = style.toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.matches("(?:heading|标题)[1-6]")) {
            return Character.getNumericValue(normalized.charAt(normalized.length() - 1));
        }
        return 0;
    }

    private boolean isTitle(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        return style != null && ("title".equalsIgnoreCase(style) || "标题".equals(style));
    }
}
