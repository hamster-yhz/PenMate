package com.penmate.backend.application.novel.export;

import com.penmate.backend.application.common.exception.BusinessException;

import java.util.Locale;

public enum NovelExportFormat {
    TXT("txt", "text/plain;charset=UTF-8"),
    MARKDOWN("md", "text/markdown;charset=UTF-8"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    EPUB("epub", "application/epub+zip");

    private final String extension;
    private final String contentType;

    NovelExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static NovelExportFormat parse(String value) {
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest("Export format is required");
        }
        if ("md".equalsIgnoreCase(value.trim())) return MARKDOWN;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("Export format must be one of [txt, markdown, docx, epub]");
        }
    }
}
