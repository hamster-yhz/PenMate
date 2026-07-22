package com.penmate.backend.infrastructure.export;

import com.penmate.backend.application.novel.export.NovelDocumentRenderer;
import com.penmate.backend.application.novel.export.NovelExportFormat;
import com.penmate.backend.application.novel.export.NovelManuscript;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class OpenXmlNovelDocumentRenderer implements NovelDocumentRenderer {

    private static final String WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();

    @Override
    public byte[] render(NovelExportFormat format, NovelManuscript manuscript) {
        return switch (format) {
            case TXT -> renderText(manuscript);
            case DOCX -> renderDocx(manuscript);
        };
    }

    private byte[] renderText(NovelManuscript manuscript) {
        StringBuilder text = new StringBuilder(manuscript.title()).append("\n\n");
        for (NovelManuscript.Volume volume : manuscript.volumes()) {
            text.append(volume.title()).append("\n\n");
            for (NovelManuscript.Chapter chapter : volume.chapters()) {
                text.append(chapter.title()).append("\n\n");
                if (!chapter.content().isEmpty()) {
                    text.append(normalizeNewlines(chapter.content())).append("\n");
                }
                text.append("\n");
            }
        }
        byte[] body = text.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF_8_BOM.length + body.length];
        System.arraycopy(UTF_8_BOM, 0, result, 0, UTF_8_BOM.length);
        System.arraycopy(body, 0, result, UTF_8_BOM.length, body.length);
        return result;
    }

    private byte[] renderDocx(NovelManuscript manuscript) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            writeContentTypes(zip);
            writePackageRelationships(zip);
            writeCoreProperties(zip, manuscript.title());
            writeAppProperties(zip);
            writeStyles(zip);
            writeDocument(zip, manuscript);
            zip.finish();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to render DOCX export", exception);
        }
    }

    private void writeContentTypes(ZipOutputStream zip) throws Exception {
        writeXml(zip, "[Content_Types].xml", writer -> {
            writer.writeStartElement("Types");
            writer.writeDefaultNamespace("http://schemas.openxmlformats.org/package/2006/content-types");
            contentType(writer, "Default", "Extension", "rels", "application/vnd.openxmlformats-package.relationships+xml");
            contentType(writer, "Default", "Extension", "xml", "application/xml");
            contentType(writer, "Override", "PartName", "/word/document.xml",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml");
            contentType(writer, "Override", "PartName", "/word/styles.xml",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml");
            contentType(writer, "Override", "PartName", "/docProps/core.xml",
                    "application/vnd.openxmlformats-package.core-properties+xml");
            contentType(writer, "Override", "PartName", "/docProps/app.xml",
                    "application/vnd.openxmlformats-officedocument.extended-properties+xml");
            writer.writeEndElement();
        });
    }

    private void contentType(XMLStreamWriter writer, String element, String key, String value, String contentType)
            throws Exception {
        writer.writeEmptyElement(element);
        writer.writeAttribute(key, value);
        writer.writeAttribute("ContentType", contentType);
    }

    private void writePackageRelationships(ZipOutputStream zip) throws Exception {
        writeXml(zip, "_rels/.rels", writer -> {
            writer.writeStartElement("Relationships");
            writer.writeDefaultNamespace(RELATIONSHIPS_NS);
            relationship(writer, "rId1",
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument",
                    "word/document.xml");
            relationship(writer, "rId2",
                    "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties",
                    "docProps/core.xml");
            relationship(writer, "rId3",
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties",
                    "docProps/app.xml");
            writer.writeEndElement();
        });
    }

    private void relationship(XMLStreamWriter writer, String id, String type, String target) throws Exception {
        writer.writeEmptyElement("Relationship");
        writer.writeAttribute("Id", id);
        writer.writeAttribute("Type", type);
        writer.writeAttribute("Target", target);
    }

    private void writeCoreProperties(ZipOutputStream zip, String title) throws Exception {
        writeXml(zip, "docProps/core.xml", writer -> {
            writer.writeStartElement("cp", "coreProperties",
                    "http://schemas.openxmlformats.org/package/2006/metadata/core-properties");
            writer.writeNamespace("cp", "http://schemas.openxmlformats.org/package/2006/metadata/core-properties");
            writer.writeNamespace("dc", "http://purl.org/dc/elements/1.1/");
            writer.writeStartElement("dc", "title", "http://purl.org/dc/elements/1.1/");
            writer.writeCharacters(title);
            writer.writeEndElement();
            writer.writeEndElement();
        });
    }

    private void writeAppProperties(ZipOutputStream zip) throws Exception {
        writeXml(zip, "docProps/app.xml", writer -> {
            writer.writeStartElement("Properties");
            writer.writeDefaultNamespace("http://schemas.openxmlformats.org/officeDocument/2006/extended-properties");
            writer.writeStartElement("Application");
            writer.writeCharacters("PenMate");
            writer.writeEndElement();
            writer.writeEndElement();
        });
    }

    private void writeStyles(ZipOutputStream zip) throws Exception {
        writeXml(zip, "word/styles.xml", writer -> {
            writer.writeStartElement("w", "styles", WORD_NS);
            writer.writeNamespace("w", WORD_NS);
            paragraphStyle(writer, "Normal", "Normal", true, null, 22, false);
            paragraphStyle(writer, "Title", "Title", false, null, 44, true);
            paragraphStyle(writer, "Heading1", "heading 1", false, 0, 36, true);
            paragraphStyle(writer, "Heading2", "heading 2", false, 1, 30, true);
            writer.writeEndElement();
        });
    }

    private void paragraphStyle(XMLStreamWriter writer, String styleId, String name, boolean defaultStyle,
                                Integer outlineLevel, int halfPointSize, boolean bold) throws Exception {
        writer.writeStartElement("w", "style", WORD_NS);
        writer.writeAttribute("w", WORD_NS, "type", "paragraph");
        writer.writeAttribute("w", WORD_NS, "styleId", styleId);
        if (defaultStyle) writer.writeAttribute("w", WORD_NS, "default", "1");
        wordValueElement(writer, "name", name);
        if (!defaultStyle) wordValueElement(writer, "basedOn", "Normal");
        writer.writeEmptyElement("w", "qFormat", WORD_NS);
        if (outlineLevel != null) {
            writer.writeStartElement("w", "pPr", WORD_NS);
            wordValueElement(writer, "outlineLvl", String.valueOf(outlineLevel));
            writer.writeEndElement();
        }
        writer.writeStartElement("w", "rPr", WORD_NS);
        if (bold) writer.writeEmptyElement("w", "b", WORD_NS);
        wordValueElement(writer, "sz", String.valueOf(halfPointSize));
        writer.writeEndElement();
        writer.writeEndElement();
    }

    private void writeDocument(ZipOutputStream zip, NovelManuscript manuscript) throws Exception {
        writeXml(zip, "word/document.xml", writer -> {
            writer.writeStartElement("w", "document", WORD_NS);
            writer.writeNamespace("w", WORD_NS);
            writer.writeStartElement("w", "body", WORD_NS);
            paragraph(writer, manuscript.title(), "Title");
            for (NovelManuscript.Volume volume : manuscript.volumes()) {
                paragraph(writer, volume.title(), "Heading1");
                for (NovelManuscript.Chapter chapter : volume.chapters()) {
                    paragraph(writer, chapter.title(), "Heading2");
                    for (String line : normalizeNewlines(chapter.content()).split("\n", -1)) {
                        paragraph(writer, line, null);
                    }
                }
            }
            writer.writeEmptyElement("w", "sectPr", WORD_NS);
            writer.writeEndElement();
            writer.writeEndElement();
        });
    }

    private void paragraph(XMLStreamWriter writer, String text, String style) throws Exception {
        writer.writeStartElement("w", "p", WORD_NS);
        if (style != null) {
            writer.writeStartElement("w", "pPr", WORD_NS);
            wordValueElement(writer, "pStyle", style);
            writer.writeEndElement();
        }
        if (text != null && !text.isEmpty()) {
            writer.writeStartElement("w", "r", WORD_NS);
            writer.writeStartElement("w", "t", WORD_NS);
            writer.writeAttribute("xml", "http://www.w3.org/XML/1998/namespace", "space", "preserve");
            writer.writeCharacters(text);
            writer.writeEndElement();
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    private void wordValueElement(XMLStreamWriter writer, String localName, String value) throws Exception {
        writer.writeEmptyElement("w", localName, WORD_NS);
        writer.writeAttribute("w", WORD_NS, "val", value);
    }

    private void writeXml(ZipOutputStream zip, String name, XmlContent content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(zip, StandardCharsets.UTF_8.name());
        writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
        content.write(writer);
        writer.writeEndDocument();
        writer.flush();
        zip.closeEntry();
    }

    private String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    @FunctionalInterface
    private interface XmlContent {
        void write(XMLStreamWriter writer) throws Exception;
    }
}
