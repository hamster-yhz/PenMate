package com.penmate.backend.domain.novel.importing;

import java.util.Locale;

public enum NovelImportFormat {
    TXT, MARKDOWN, DOCX;

    public static NovelImportFormat fromFilename(String filename) {
        String normalized = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".txt")) return TXT;
        if (normalized.endsWith(".md") || normalized.endsWith(".markdown")) return MARKDOWN;
        if (normalized.endsWith(".docx")) return DOCX;
        throw new IllegalArgumentException("Supported file types are TXT, Markdown, and DOCX");
    }
}
