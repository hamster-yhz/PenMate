package com.penmate.backend.infrastructure.export;

import com.penmate.backend.application.novel.export.NovelExportFormat;
import com.penmate.backend.application.novel.export.NovelManuscript;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class OpenXmlNovelDocumentRendererTest {

    private static final String WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private final OpenXmlNovelDocumentRenderer renderer = new OpenXmlNovelDocumentRenderer();
    private final NovelManuscript manuscript = new NovelManuscript(
            "长夜",
            List.of(new NovelManuscript.Volume(
                    "第一卷",
                    List.of(new NovelManuscript.Chapter("第一章", "第一段。\r\n\r\n第二段。"))
            ))
    );

    @Test
    void renders_utf8_text_with_bom_and_readable_structure() {
        byte[] result = renderer.render(NovelExportFormat.TXT, manuscript);

        assertThat(result).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String content = new String(result, 3, result.length - 3, StandardCharsets.UTF_8);
        assertThat(content).contains("长夜\n\n第一卷\n\n第一章\n\n第一段。\n\n第二段。");
    }

    @Test
    void renders_a_complete_openxml_package_with_heading_styles_and_body_text() throws Exception {
        Map<String, byte[]> entries = unzip(renderer.render(NovelExportFormat.DOCX, manuscript));

        assertThat(entries.keySet()).contains(
                "[Content_Types].xml",
                "_rels/.rels",
                "docProps/core.xml",
                "docProps/app.xml",
                "word/styles.xml",
                "word/document.xml"
        );
        Document document = parse(entries.get("word/document.xml"));
        assertThat(document.getElementsByTagNameNS(WORD_NS, "t").getLength()).isEqualTo(5);
        assertThat(document.getElementsByTagNameNS(WORD_NS, "t").item(0).getTextContent()).isEqualTo("长夜");
        assertThat(document.getElementsByTagNameNS(WORD_NS, "t").item(4).getTextContent()).isEqualTo("第二段。");
        String documentXml = new String(entries.get("word/document.xml"), StandardCharsets.UTF_8);
        assertThat(documentXml).contains("w:val=\"Title\"")
                .contains("w:val=\"Heading1\"")
                .contains("w:val=\"Heading2\"");
        assertThat(new String(entries.get("docProps/core.xml"), StandardCharsets.UTF_8)).contains("长夜");
    }

    private Map<String, byte[]> unzip(byte[] value) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(value), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                zip.transferTo(output);
                entries.put(entry.getName(), output.toByteArray());
            }
        }
        return entries;
    }

    private Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }
}
