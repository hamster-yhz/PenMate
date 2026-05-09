package com.penmate.backend.application.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.rag.model.RagRetrievalLog;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import com.penmate.backend.domain.rag.repository.RagRetrievalRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTest extends BaseApplicationServiceTest {

    @Mock
    private RagRetrievalRepository ragRetrievalRepository;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

    @InjectMocks
    private RagRetrievalService ragRetrievalService;

    @Test
    void UT_APP_RAG_RETRIEVE_SHOULD_ASSIGN_RETRIEVAL_LOG_BUSINESS_ID_BEFORE_INSERT() {
        RagRetrievedChunk chunk = new RagRetrievedChunk();
        chunk.setDocumentId(801L);
        chunk.setDocumentTitle("知识片段");
        chunk.setChunkNo(1);
        chunk.setContentText("content");

        when(businessIdGenerator.nextId()).thenReturn(910001L);
        when(ragRetrievalRepository.searchChunks(1L, "hero", 3)).thenReturn(List.of(chunk));
        when(ragRetrievalRepository.insertRetrievalLog(any(RagRetrievalLog.class))).thenAnswer(invocation -> {
            RagRetrievalLog log = invocation.getArgument(0);
            log.setId(77L);
            return 1;
        });

        RagRetrievalService service = new RagRetrievalService(ragRetrievalRepository, businessIdGenerator, new ObjectMapper());

        RagRetrievalService.RetrievalResult result = service.retrieve(1L, 11L, "hero", "trace-rag-1");

        ArgumentCaptor<RagRetrievalLog> captor = ArgumentCaptor.forClass(RagRetrievalLog.class);
        verify(ragRetrievalRepository).insertRetrievalLog(captor.capture());
        RagRetrievalLog inserted = captor.getValue();
        assertThat(inserted.getRetrievalLogId()).isEqualTo(910001L);
        assertThat(inserted.getProjectId()).isEqualTo(1L);
        assertThat(inserted.getTaskId()).isEqualTo(11L);
        assertThat(inserted.getTraceId()).isEqualTo("trace-rag-1");
        assertThat(result.logId()).isEqualTo(77L);
        verify(ragRetrievalRepository).searchChunks(1L, "hero", 3);
        verify(businessIdGenerator).nextId();
    }
}
