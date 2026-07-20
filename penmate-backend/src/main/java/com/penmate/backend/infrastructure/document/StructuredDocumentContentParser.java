package com.penmate.backend.infrastructure.document;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.rag.service.DocumentContentParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
public class StructuredDocumentContentParser implements DocumentContentParser {
    private static final Set<String> TEXT_MIMES = Set.of("text/plain", "text/markdown", "text/x-markdown");
    private static final Set<String> HTML_MIMES = Set.of("text/html", "application/xhtml+xml");

    @Override
    public ParsedDocument parse(String fileExtension, String mimeType, byte[] content) {
        String extension = normalizeExtension(fileExtension);
        String declaredMime = normalizeMime(mimeType);
        String text = decodeStrictUtf8(content);
        rejectBinaryControlCharacters(text);
        if (Set.of("html", "htm").contains(extension)) {
            if (!HTML_MIMES.contains(declaredMime)) throw BusinessException.badRequest("HTML extension and MIME type do not match");
            return new ParsedDocument(parseHtml(text), "text/html");
        }
        if (!Set.of("txt", "md", "markdown").contains(extension)) {
            throw BusinessException.badRequest("Only TXT, Markdown, and HTML documents are supported");
        }
        if (!TEXT_MIMES.contains(declaredMime)) throw BusinessException.badRequest("Text extension and MIME type do not match");
        if (looksLikeHtml(text)) throw BusinessException.badRequest("Document content does not match its extension");
        return new ParsedDocument(normalizeText(text), "txt".equals(extension) ? "text/plain" : "text/markdown");
    }

    private String parseHtml(String html) {
        Document document = Jsoup.parse(html);
        if (document.body() == null || document.body().text().isBlank()) {
            throw BusinessException.badRequest("HTML document has no readable content");
        }
        document.select("script,style,noscript,template,svg,canvas").remove();
        for (Element element : document.select("h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,br,hr")) {
            element.after("\n");
        }
        return normalizeText(document.body().wholeText());
    }

    private String decodeStrictUtf8(byte[] content) {
        if (content == null || content.length == 0) throw BusinessException.badRequest("Document is empty");
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            throw BusinessException.badRequest("Document must be valid UTF-8");
        }
    }

    private void rejectBinaryControlCharacters(String text) {
        long invalid = text.chars().filter(value -> value == 0 || (value < 0x09) || (value > 0x0D && value < 0x20)).count();
        if (invalid > 0) throw BusinessException.badRequest("Binary content is not supported");
    }

    private boolean looksLikeHtml(String text) {
        String normalized = text.stripLeading().toLowerCase(Locale.ROOT);
        return normalized.startsWith("<!doctype html") || normalized.startsWith("<html") || normalized.startsWith("<body");
    }

    private String normalizeText(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (normalized.isBlank()) throw BusinessException.badRequest("Document has no readable content");
        return normalized;
    }

    private String normalizeExtension(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
    }

    private String normalizeMime(String value) {
        if (value == null) return "";
        return value.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    }
}
