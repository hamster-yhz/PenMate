package com.penmate.backend.application.rag;

public record RagIndexingSettings(int maxChunksPerSource, int maxChunksPerProject,
                                  int embeddingBatchSize, int embeddingBatchMaxCharacters) {

    public RagIndexingSettings {
        if (maxChunksPerSource < 1) throw new IllegalArgumentException("maxChunksPerSource must be positive");
        if (maxChunksPerProject < 1) throw new IllegalArgumentException("maxChunksPerProject must be positive");
        if (embeddingBatchSize < 1) throw new IllegalArgumentException("embeddingBatchSize must be positive");
        if (embeddingBatchMaxCharacters < 1) {
            throw new IllegalArgumentException("embeddingBatchMaxCharacters must be positive");
        }
    }
}
