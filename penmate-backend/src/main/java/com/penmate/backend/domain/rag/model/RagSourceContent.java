package com.penmate.backend.domain.rag.model;

public record RagSourceContent(String sourceType, Long sourceId, String sourceRevision, String title,
                               String inlineContent, String objectKey, String fileExtension,
                               String mimeType) {
}
