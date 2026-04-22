package com.penmate.backend.application.rag;

import com.penmate.backend.application.rag.command.CreateRagDocumentCommand;
import com.penmate.backend.application.rag.command.OperateRagDocumentCommand;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.domain.rag.model.RagRetrievalLog;
import com.penmate.backend.domain.rag.repository.RagDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private RagDocumentRepository ragDocumentRepository;

    @Mock
    private RagRetrievalService ragRetrievalService;

    @InjectMocks
    private RagApplicationService ragApplicationService;

    @Test
    void UT_APP_RAG_LIST_DOCUMENTS_SUCCESS() {
        when(ragDocumentRepository.findByProjectId(1L)).thenReturn(List.of(new RagDocument(), new RagDocument()));

        List<RagDocument> result = ragApplicationService.listDocuments(1L);

        assertThat(result).hasSize(2);
        verify(ragDocumentRepository).findByProjectId(1L);
    }

    @Test
    void UT_APP_RAG_CREATE_DOCUMENT_SUCCESS() {
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-RAG-CREATE";

        when(ragDocumentRepository.insert(any(RagDocument.class))).thenAnswer(invocation -> {
            RagDocument doc = invocation.getArgument(0);
            doc.setId(7L);
            return 1;
        });

        RagDocument result = ragApplicationService.createDocument(
                1L,
                new CreateRagDocumentCommand("manual", "知识文档", "src", "obj-key", "etag", "text/markdown", operatorId),
                traceId
        );

        assertThat(result.getId()).isEqualTo(7L);
        verify(ragDocumentRepository).insert(any(RagDocument.class));
    }

    @Test
    void UT_APP_RAG_GET_DOCUMENT_NOT_FOUND() {
        when(ragDocumentRepository.findById(1L, 99L)).thenReturn(null);

        assertThatThrownBy(() -> ragApplicationService.getDocument(1L, 99L))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Rag document not found");
    }

    @Test
    void UT_APP_RAG_DELETE_DOCUMENT_NOT_FOUND() {
        when(ragDocumentRepository.softDelete(1L, 99L)).thenReturn(0);

        assertThatThrownBy(() -> ragApplicationService.deleteDocument(1L, 99L, new OperateRagDocumentCommand(1001L), "trace"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Rag document not found");
    }

    @Test
    void UT_APP_RAG_PARSE_DOCUMENT_SUCCESS() {
        Long projectId = 1L;
        Long docId = 2L;
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-RAG-PARSE";

        RagDocument current = new RagDocument();
        current.setId(docId);
        current.setParseStatus("pending");
        RagDocument parsed = new RagDocument();
        parsed.setId(docId);
        parsed.setParseStatus("done");
        parsed.setIndexStatus("pending");

        when(ragDocumentRepository.findById(projectId, docId)).thenReturn(current, parsed);
        when(ragDocumentRepository.updateStatuses(projectId, docId, "done", "pending")).thenReturn(1);

        RagDocument result = ragApplicationService.parseDocument(projectId, docId, new OperateRagDocumentCommand(operatorId), traceId);

        assertThat(result.getParseStatus()).isEqualTo("done");
        verify(ragDocumentRepository).updateStatuses(projectId, docId, "done", "pending");
    }

    @Test
    void UT_APP_RAG_EMBED_DOCUMENT_PARSE_NOT_FINISHED() {
        RagDocument current = new RagDocument();
        current.setId(2L);
        current.setParseStatus("pending");
        when(ragDocumentRepository.findById(1L, 2L)).thenReturn(current);

        assertThatThrownBy(() -> ragApplicationService.embedDocument(1L, 2L, new OperateRagDocumentCommand(1001L), "trace"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Document parse not finished");
    }

    @Test
    void UT_APP_RAG_GET_INDEX_STATUS_SUCCESS() {
        RagDocument document = new RagDocument();
        document.setId(2L);
        document.setParseStatus("done");
        document.setIndexStatus("done");
        when(ragDocumentRepository.findById(1L, 2L)).thenReturn(document);

        Map<String, Object> result = ragApplicationService.getIndexStatus(1L, 2L);

        assertThat(result).containsEntry("docId", 2L)
                .containsEntry("parseStatus", "done")
                .containsEntry("indexStatus", "done");
        verify(ragDocumentRepository).findById(1L, 2L);
        verifyNoMoreInteractions(ragDocumentRepository);
    }

    @Test
    void UT_APP_RAG_LIST_RETRIEVAL_LOGS_SUCCESS() {
        RagRetrievalLog log = new RagRetrievalLog();
        log.setId(1L);
        log.setProjectId(1L);
        log.setTaskId(2L);
        log.setHitCount(3);
        log.setSourcesJson("[]");
        log.setAdopted(true);
        when(ragRetrievalService.listRetrievalLogs(1L)).thenReturn(List.of(log));

        List<Map<String, Object>> result = ragApplicationService.listRetrievalLogs(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("projectId", 1L)
                .containsEntry("taskId", 2L)
                .containsEntry("hitCount", 3);
    }
}

