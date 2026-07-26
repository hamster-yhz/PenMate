package com.penmate.backend.infrastructure.importing;

import com.penmate.backend.application.novel.importing.NovelImportSourceParser;
import com.penmate.backend.domain.novel.importing.NovelImportDraft;
import com.penmate.backend.domain.novel.importing.NovelImportFormat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class EpubNovelImportSourceParser implements NovelImportSourceParser {
    private static final int MAX_ENTRIES = 5_000;
    private static final int MAX_ENTRY_BYTES = 20 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    private static final Set<String> BLOCK_TAGS = Set.of(
            "address", "article", "aside", "blockquote", "div", "footer", "header",
            "h1", "h2", "h3", "h4", "h5", "h6", "li", "p", "pre"
    );

    @Override public NovelImportFormat format() { return NovelImportFormat.EPUB; }

    @Override
    public NovelImportDraft parse(String filename, InputStream input) throws IOException {
        Map<String, byte[]> entries = readArchive(input);
        byte[] mimetype = entries.get("mimetype");
        if (mimetype == null || !"application/epub+zip".equals(new String(mimetype, StandardCharsets.US_ASCII).strip())) {
            throw new IllegalArgumentException("The uploaded file is not a valid EPUB document");
        }

        Document container = parseXml(required(entries, "META-INF/container.xml"));
        NodeList rootfiles = container.getElementsByTagNameNS("*", "rootfile");
        if (rootfiles.getLength() == 0) throw new IllegalArgumentException("EPUB package location is missing");
        org.w3c.dom.Node fullPath = rootfiles.item(0).getAttributes().getNamedItem("full-path");
        if (fullPath == null || fullPath.getNodeValue().isBlank()) {
            throw new IllegalArgumentException("EPUB package location is missing");
        }
        String packagePath = normalizePath(fullPath.getNodeValue(), false);
        Document packageDocument = parseXml(required(entries, packagePath));
        String packageDirectory = directoryOf(packagePath);

        String projectTitle = firstText(packageDocument, "title");
        if (projectTitle.isBlank()) projectTitle = TxtNovelImportSourceParser.titleFromFilename(filename, ".epub");

        Map<String, ManifestItem> manifest = readManifest(packageDocument, packageDirectory);
        Navigation navigation = readNavigation(entries, manifest);
        List<MutableVolume> volumes = readSpine(entries, packageDocument, manifest, navigation);
        if (volumes.stream().mapToInt(volume -> volume.chapters.size()).sum() == 0) {
            throw new IllegalArgumentException("EPUB does not contain readable chapters");
        }

        List<NovelImportDraft.Volume> result = volumes.stream()
                .filter(volume -> !volume.chapters.isEmpty())
                .map(volume -> new NovelImportDraft.Volume(volume.title, List.copyOf(volume.chapters)))
                .toList();
        return new NovelImportDraft(projectTitle, format(), result, List.of()).withDiagnostics();
    }

    private Map<String, byte[]> readArchive(InputStream input) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            int count = 0;
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++count > MAX_ENTRIES) throw new IllegalArgumentException("EPUB contains too many files");
                String path = normalizePath(entry.getName(), false);
                if (entry.isDirectory()) continue;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                int entryBytes = 0;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    total += read;
                    if (entryBytes > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                        throw new IllegalArgumentException("EPUB expands beyond the import size limit");
                    }
                    output.write(buffer, 0, read);
                }
                if (entries.putIfAbsent(path, output.toByteArray()) != null) {
                    throw new IllegalArgumentException("EPUB contains duplicate file paths");
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("The uploaded file is not a valid EPUB archive", exception);
        }
        return entries;
    }

    private Map<String, ManifestItem> readManifest(Document document, String packageDirectory) {
        Map<String, ManifestItem> manifest = new HashMap<>();
        NodeList items = document.getElementsByTagNameNS("*", "item");
        for (int index = 0; index < items.getLength(); index++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) items.item(index);
            String id = element.getAttribute("id");
            String href = element.getAttribute("href");
            if (id.isBlank() || href.isBlank()) continue;
            manifest.put(id, new ManifestItem(
                    resolvePath(packageDirectory, href),
                    element.getAttribute("media-type"),
                    element.getAttribute("properties")
            ));
        }
        return manifest;
    }

    private Navigation readNavigation(Map<String, byte[]> entries, Map<String, ManifestItem> manifest) {
        ManifestItem navItem = manifest.values().stream()
                .filter(item -> hasToken(item.properties(), "nav"))
                .findFirst().orElse(null);
        if (navItem == null || !entries.containsKey(navItem.path())) return Navigation.EMPTY;

        org.jsoup.nodes.Document document = Jsoup.parse(
                new String(entries.get(navItem.path()), StandardCharsets.UTF_8), "", Parser.xmlParser());
        Element navigation = document.getAllElements().stream()
                .filter(element -> "nav".equalsIgnoreCase(element.tagName()))
                .filter(element -> hasToken(element.attr("epub:type"), "toc") || "toc".equalsIgnoreCase(element.id()))
                .findFirst().orElseGet(() -> document.selectFirst("nav"));
        if (navigation == null) return Navigation.EMPTY;

        Map<String, String> titles = new HashMap<>();
        Map<String, String> volumes = new HashMap<>();
        String navDirectory = directoryOf(navItem.path());
        for (Element anchor : navigation.select("a[href]")) {
            String path;
            try {
                path = resolvePath(navDirectory, anchor.attr("href"));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            String title = anchor.text().strip();
            if (!title.isEmpty()) titles.putIfAbsent(path, title);
            String volume = parentNavigationLabel(anchor);
            if (!volume.isBlank()) volumes.putIfAbsent(path, volume);
        }
        return new Navigation(titles, volumes);
    }

    private List<MutableVolume> readSpine(Map<String, byte[]> entries, Document packageDocument,
                                          Map<String, ManifestItem> manifest, Navigation navigation) {
        List<MutableVolume> volumes = new ArrayList<>();
        MutableVolume currentVolume = null;
        NodeList itemrefs = packageDocument.getElementsByTagNameNS("*", "itemref");
        if (itemrefs.getLength() > NovelImportDraft.MAX_CHAPTERS) {
            throw new IllegalArgumentException("EPUB contains more than 2000 spine items");
        }
        for (int index = 0; index < itemrefs.getLength(); index++) {
            org.w3c.dom.Element itemref = (org.w3c.dom.Element) itemrefs.item(index);
            if ("no".equalsIgnoreCase(itemref.getAttribute("linear"))) continue;
            ManifestItem item = manifest.get(itemref.getAttribute("idref"));
            if (item == null || !isHtml(item.mediaType()) || !entries.containsKey(item.path())
                    || hasToken(item.properties(), "nav")) continue;

            ParsedChapter chapter = parseChapter(entries.get(item.path()), navigation.titles().get(item.path()), item.path());
            String volumeTitle = navigation.volumes().getOrDefault(item.path(), "第一卷");
            if (currentVolume == null || !currentVolume.title.equals(volumeTitle)) {
                currentVolume = new MutableVolume(volumeTitle);
                volumes.add(currentVolume);
            }
            currentVolume.chapters.add(new NovelImportDraft.Chapter(chapter.title(), chapter.content()));
        }
        return volumes;
    }

    private ParsedChapter parseChapter(byte[] value, String navigationTitle, String path) {
        org.jsoup.nodes.Document document = Jsoup.parse(new String(value, StandardCharsets.UTF_8), "", Parser.xmlParser());
        Element body = document.selectFirst("body");
        if (body == null) body = document;
        Element heading = body.selectFirst("h1, h2, h3");
        String title = heading == null ? "" : heading.text().strip();
        if (title.isBlank() && navigationTitle != null) title = navigationTitle.strip();
        if (title.isBlank()) title = document.title().strip();
        if (title.isBlank()) title = filenameTitle(path);

        Element contentRoot = body.clone();
        contentRoot.select("script, style, nav, svg").remove();
        Element contentHeading = contentRoot.selectFirst("h1, h2, h3");
        if (heading != null && contentHeading != null) contentHeading.remove();
        StringBuilder content = new StringBuilder();
        for (Node node : contentRoot.childNodes()) appendText(node, content);
        return new ParsedChapter(title, normalizeContent(content.toString()));
    }

    private void appendText(Node node, StringBuilder target) {
        if (node instanceof TextNode text) {
            target.append(text.getWholeText());
            return;
        }
        if (!(node instanceof Element element)) return;
        String tag = element.normalName().toLowerCase(Locale.ROOT);
        if ("br".equals(tag)) {
            target.append('\n');
            return;
        }
        boolean block = BLOCK_TAGS.contains(tag);
        if (block) appendParagraphBreak(target);
        for (Node child : element.childNodes()) appendText(child, target);
        if (block) appendParagraphBreak(target);
    }

    private void appendParagraphBreak(StringBuilder target) {
        int length = target.length();
        if (length == 0) return;
        if (target.charAt(length - 1) != '\n') target.append('\n');
        if (target.length() < 2 || target.charAt(target.length() - 2) != '\n') target.append('\n');
    }

    private String normalizeContent(String value) {
        String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> result = new ArrayList<>();
        boolean previousBlank = true;
        for (String line : lines) {
            String normalized = line.strip();
            if (normalized.isEmpty()) {
                if (!previousBlank) result.add("");
                previousBlank = true;
            } else {
                result.add(normalized);
                previousBlank = false;
            }
        }
        while (!result.isEmpty() && result.getLast().isEmpty()) result.removeLast();
        return String.join("\n", result);
    }

    private static Document parseXml(byte[] value) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(value));
        } catch (Exception exception) {
            throw new IllegalArgumentException("EPUB contains invalid package metadata", exception);
        }
    }

    private static String firstText(Document document, String localName) {
        NodeList values = document.getElementsByTagNameNS("*", localName);
        return values.getLength() == 0 ? "" : values.item(0).getTextContent().strip();
    }

    private static byte[] required(Map<String, byte[]> entries, String path) {
        byte[] value = entries.get(path);
        if (value == null) throw new IllegalArgumentException("EPUB package file is missing: " + path);
        return value;
    }

    private static String parentNavigationLabel(Element anchor) {
        Element chapterLi = anchor.closest("li");
        if (chapterLi == null || chapterLi.parent() == null) return "";
        Element parentList = chapterLi.parent();
        Element volumeLi = parentList.parent();
        if (volumeLi == null || !"li".equalsIgnoreCase(volumeLi.tagName())) return "";
        for (Element child : volumeLi.children()) {
            if (child == parentList) break;
            if ("span".equalsIgnoreCase(child.tagName()) && !child.text().isBlank()) {
                return child.text().strip();
            }
        }
        return "";
    }

    private static boolean isHtml(String mediaType) {
        return "application/xhtml+xml".equalsIgnoreCase(mediaType)
                || "text/html".equalsIgnoreCase(mediaType);
    }

    private static boolean hasToken(String value, String token) {
        if (value == null) return false;
        for (String candidate : value.strip().split("\\s+")) {
            if (candidate.equalsIgnoreCase(token)) return true;
        }
        return false;
    }

    private static String directoryOf(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? "" : path.substring(0, separator + 1);
    }

    private static String filenameTitle(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        int extension = filename.lastIndexOf('.');
        return extension > 0 ? filename.substring(0, extension) : filename;
    }

    private static String resolvePath(String baseDirectory, String href) {
        try {
            URI uri = URI.create(href);
            if (uri.isAbsolute() || uri.getRawAuthority() != null) throw new IllegalArgumentException("External EPUB links are not supported");
            String path = uri.getPath();
            if (path == null || path.isBlank()) throw new IllegalArgumentException("Empty EPUB path");
            return normalizePath(baseDirectory + path, true);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid EPUB resource path", exception);
        }
    }

    private static String normalizePath(String value, boolean allowParent) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.startsWith("\\")
                || value.matches("^[A-Za-z]:.*")
                || value.contains("\\") || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid EPUB archive path");
        }
        ArrayDeque<String> parts = new ArrayDeque<>();
        for (String part : value.split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!allowParent || parts.isEmpty()) throw new IllegalArgumentException("EPUB path escapes the archive");
                parts.removeLast();
            } else {
                parts.addLast(part);
            }
        }
        if (parts.isEmpty()) throw new IllegalArgumentException("Invalid EPUB archive path");
        return String.join("/", parts);
    }

    private record ManifestItem(String path, String mediaType, String properties) { }
    private record Navigation(Map<String, String> titles, Map<String, String> volumes) {
        private static final Navigation EMPTY = new Navigation(Map.of(), Map.of());
    }
    private record ParsedChapter(String title, String content) { }
    private static final class MutableVolume {
        private final String title;
        private final List<NovelImportDraft.Chapter> chapters = new ArrayList<>();
        private MutableVolume(String title) { this.title = title; }
    }
}
