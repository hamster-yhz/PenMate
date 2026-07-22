package com.penmate.backend.infrastructure.rag;

import com.penmate.backend.application.rag.RagApplicationSettings;
import com.penmate.backend.application.rag.RagIndexingSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagSettingsConfiguration {

    @Bean
    RagApplicationSettings ragApplicationSettings(
            @Value("${penmate.indexing.max-upload-bytes:10485760}") long maxUploadBytes,
            @Value("${penmate.storage.presign-expire-minutes:15}") long uploadTtlMinutes) {
        return new RagApplicationSettings(maxUploadBytes, uploadTtlMinutes);
    }

    @Bean
    RagIndexingSettings ragIndexingSettings(
            @Value("${penmate.indexing.max-chunks-per-source:20000}") int maxChunksPerSource,
            @Value("${penmate.indexing.max-chunks-per-project:100000}") int maxChunksPerProject,
            @Value("${penmate.indexing.embedding-batch-size:32}") int embeddingBatchSize,
            @Value("${penmate.indexing.embedding-batch-max-characters:30000}") int embeddingBatchMaxCharacters) {
        return new RagIndexingSettings(maxChunksPerSource, maxChunksPerProject,
                embeddingBatchSize, embeddingBatchMaxCharacters);
    }
}
