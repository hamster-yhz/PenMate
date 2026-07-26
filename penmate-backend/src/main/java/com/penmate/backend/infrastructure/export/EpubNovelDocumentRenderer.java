package com.penmate.backend.infrastructure.export;

import com.penmate.backend.application.novel.export.NovelManuscript;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class EpubNovelDocumentRenderer {
    private static final byte[] MIMETYPE = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);

    private EpubNovelDocumentRenderer() { }

    static byte[] render(NovelManuscript manuscript) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            writeMimetype(zip);
            write(zip, "META-INF/container.xml", container());

            List<ChapterFile> chapters = chapterFiles(manuscript);
            write(zip, "EPUB/package.opf", packageDocument(manuscript, chapters));
            write(zip, "EPUB/nav.xhtml", navigationDocument(manuscript, chapters));
            write(zip, "EPUB/styles/book.css", stylesheet());
            for (ChapterFile chapter : chapters) {
                write(zip, "EPUB/" + chapter.href(), chapterDocument(manuscript.title(), chapter));
            }
            zip.finish();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to render EPUB export", exception);
        }
    }

    private static List<ChapterFile> chapterFiles(NovelManuscript manuscript) {
        List<ChapterFile> files = new ArrayList<>();
        int index = 1;
        for (NovelManuscript.Volume volume : manuscript.volumes()) {
            for (NovelManuscript.Chapter chapter : volume.chapters()) {
                files.add(new ChapterFile(
                        "chapter-" + index,
                        "text/chapter-%04d.xhtml".formatted(index),
                        chapter.title(), chapter.content()
                ));
                index++;
            }
        }
        return files;
    }

    private static void writeMimetype(ZipOutputStream zip) throws Exception {
        CRC32 crc = new CRC32();
        crc.update(MIMETYPE);
        ZipEntry entry = new ZipEntry("mimetype");
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(MIMETYPE.length);
        entry.setCompressedSize(MIMETYPE.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(MIMETYPE);
        zip.closeEntry();
    }

    private static void write(ZipOutputStream zip, String path, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String container() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """;
    }

    private static String packageDocument(NovelManuscript manuscript, List<ChapterFile> chapters) {
        StringBuilder manifest = new StringBuilder();
        StringBuilder spine = new StringBuilder();
        for (ChapterFile chapter : chapters) {
            manifest.append("    <item id=\"").append(chapter.id()).append("\" href=\"")
                    .append(chapter.href()).append("\" media-type=\"application/xhtml+xml\"/>\n");
            spine.append("    <itemref idref=\"").append(chapter.id()).append("\"/>\n");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" xml:lang="zh-CN">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="book-id">urn:uuid:%s</dc:identifier>
                    <dc:title>%s</dc:title>
                    <dc:language>zh-CN</dc:language>
                    <meta property="dcterms:modified">%s</meta>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="style" href="styles/book.css" media-type="text/css"/>
                %s  </manifest>
                  <spine>
                %s  </spine>
                </package>
                """.formatted(
                UUID.randomUUID(), xml(manuscript.title()),
                Instant.now().truncatedTo(ChronoUnit.SECONDS), manifest, spine
        );
    }

    private static String navigationDocument(NovelManuscript manuscript, List<ChapterFile> chapters) {
        StringBuilder items = new StringBuilder();
        int chapterIndex = 0;
        for (NovelManuscript.Volume volume : manuscript.volumes()) {
            items.append("      <li><span>").append(xml(volume.title())).append("</span><ol>\n");
            for (int ignored = 0; ignored < volume.chapters().size(); ignored++) {
                ChapterFile chapter = chapters.get(chapterIndex++);
                items.append("        <li><a href=\"").append(chapter.href()).append("\">")
                        .append(xml(chapter.title())).append("</a></li>\n");
            }
            items.append("      </ol></li>\n");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="zh-CN" xml:lang="zh-CN">
                <head><title>%s</title><link rel="stylesheet" type="text/css" href="styles/book.css"/></head>
                <body><nav epub:type="toc" id="toc"><h1>目录</h1><ol>
                %s</ol></nav></body>
                </html>
                """.formatted(xml(manuscript.title()), items);
    }

    private static String chapterDocument(String bookTitle, ChapterFile chapter) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN" xml:lang="zh-CN">
                <head><title>%s - %s</title><link rel="stylesheet" type="text/css" href="../styles/book.css"/></head>
                <body><section epub:type="chapter" xmlns:epub="http://www.idpf.org/2007/ops">
                  <h1>%s</h1>
                %s</section></body>
                </html>
                """.formatted(xml(bookTitle), xml(chapter.title()), xml(chapter.title()), paragraphs(chapter.content()));
    }

    private static String paragraphs(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.isEmpty()) return "  <p></p>\n";
        StringBuilder result = new StringBuilder();
        for (String paragraph : normalized.split("\n[\\t ]*\n+")) {
            result.append("  <p>").append(xml(paragraph).replace("\n", "<br/>"))
                    .append("</p>\n");
        }
        return result.toString();
    }

    private static String stylesheet() {
        return """
                body { font-family: serif; line-height: 1.8; margin: 5%; }
                h1 { text-align: center; margin: 2em 0; }
                p { margin: 0.8em 0; text-indent: 2em; }
                nav ol { list-style: none; padding-left: 1.5em; }
                """;
    }

    private static String xml(String value) {
        StringBuilder valid = new StringBuilder(value.length());
        value.codePoints().filter(codePoint -> codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                        || codePoint >= 0x20 && codePoint <= 0xD7FF
                        || codePoint >= 0xE000 && codePoint <= 0xFFFD
                        || codePoint >= 0x10000 && codePoint <= 0x10FFFF)
                .forEach(valid::appendCodePoint);
        return valid.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private record ChapterFile(String id, String href, String title, String content) { }
}
