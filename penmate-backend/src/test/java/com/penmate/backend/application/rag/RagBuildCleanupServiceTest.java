package com.penmate.backend.application.rag;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.ops.AsyncJobQueueService;
import com.penmate.backend.domain.rag.repository.RagIndexRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagBuildCleanupServiceTest {
    @Test
    void enqueuesEverySupersededBuildWithAnIdempotentBusinessKey() {
        RagIndexRepository indexes = mock(RagIndexRepository.class);
        AsyncJobQueueService jobs = mock(AsyncJobQueueService.class);
        JsonCodec json = mock(JsonCodec.class);
        when(indexes.findSupersededBuilds()).thenReturn(List.of(
                new RagIndexRepository.BuildCleanupCandidate(31L, 21L, 11L),
                new RagIndexRepository.BuildCleanupCandidate(32L, 22L, 12L)));
        when(json.write(org.mockito.ArgumentMatchers.any())).thenReturn("{}");
        RagBuildCleanupService service = new RagBuildCleanupService(indexes, jobs, json);

        assertThat(service.enqueueSupersededBuilds()).isEqualTo(2);

        verify(jobs).enqueue("RAG_CLEANUP_EMBEDDING_SPACE", "rag:build:31:cleanup", 11L, 21L, "{}");
        verify(jobs).enqueue("RAG_CLEANUP_EMBEDDING_SPACE", "rag:build:32:cleanup", 12L, 22L, "{}");
    }
}
