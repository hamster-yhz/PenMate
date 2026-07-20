package com.penmate.backend.domain.rag.service;

public interface DocumentContentParser {
    ParsedDocument parse(String fileExtension, String mimeType, byte[] content);

    record ParsedDocument(String normalizedText, String detectedMimeType) {
    }
}
