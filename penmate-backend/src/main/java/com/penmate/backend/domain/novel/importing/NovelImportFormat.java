package com.penmate.backend.domain.novel.importing;

import java.util.Locale;

public enum NovelImportFormat {
    TXT, MARKDOWN, DOCX, EPUB;

    public static NovelImportFormat fromFilename(String filename) {
        String normalized = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".txt")) return TXT;
        if (normalized.endsWith(".md") || normalized.endsWith(".markdown")) return MARKDOWN;
        if (normalized.endsWith(".docx")) return DOCX;
        if (normalized.endsWith(".epub")) return EPUB;
        throw new IllegalArgumentException("Supported file types are TXT, Markdown, DOCX, and EPUB");
    }
}
