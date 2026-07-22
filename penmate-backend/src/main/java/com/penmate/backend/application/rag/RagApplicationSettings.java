package com.penmate.backend.application.rag;

public record RagApplicationSettings(long maxUploadBytes, long uploadTtlMinutes) {

    public RagApplicationSettings {
        if (maxUploadBytes < 1) throw new IllegalArgumentException("maxUploadBytes must be positive");
        if (uploadTtlMinutes < 1) throw new IllegalArgumentException("uploadTtlMinutes must be positive");
    }
}
