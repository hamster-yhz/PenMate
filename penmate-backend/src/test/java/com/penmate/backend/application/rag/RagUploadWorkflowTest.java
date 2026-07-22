package com.penmate.backend.application.rag;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.ops.AsyncJobQueueService;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.domain.rag.model.RagUploadSession;
import com.penmate.backend.domain.rag.repository.RagDocumentRepository;
import com.penmate.backend.domain.rag.repository.RagUploadSessionRepository;
import com.penmate.backend.domain.rag.service.DocumentContentParser;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagUploadWorkflowTest {
    @Mock private RagDocumentRepository documents;
    @Mock private RagUploadSessionRepository uploads;
    @Mock private NovelGateway novels;
    @Mock private BusinessIdGenerator ids;
    @Mock private RagRetrievalService retrieval;
    @Mock private ObjectStorageService storage;
    @Mock private DocumentContentParser parser;
    @Mock private AsyncJobQueueService jobs;
    @Mock private JsonCodec jsonCodec;
    private RagApplicationService service;

    @BeforeEach
    void setUp() {
        service = new RagApplicationService(documents, uploads, novels, ids, retrieval, storage, parser, jobs,
                jsonCodec, new RagApplicationSettings(10_485_760L, 15L));
        NovelProject project = new NovelProject();
        project.setProjectId(101L);
        project.setOwnerUserId(7L);
        when(novels.findProjectById(101L)).thenReturn(project);
    }

    @Test
    void initializeCreatesServerOwnedKeyAndStoresOnlyTokenHash() {
        when(ids.nextId()).thenReturn(501L);
        when(uploads.insert(any())).thenReturn(1);
        when(storage.buildUploadUrl("novels/101/rag/uploads/501.md", "text/markdown")).thenReturn("https://storage/upload");

        var result = service.initializeUpload(101L, 7L,
                new RagApplicationService.UploadRequest("notes.md", "Notes", "text/markdown", 12L, null));

        ArgumentCaptor<RagUploadSession> captor = ArgumentCaptor.forClass(RagUploadSession.class);
        verify(uploads).insert(captor.capture());
        assertThat(result.objectKey()).isEqualTo("novels/101/rag/uploads/501.md");
        assertThat(result.uploadToken()).isNotBlank();
        assertThat(captor.getValue().getUploadTokenHash()).hasSize(64).isNotEqualTo(result.uploadToken());
        assertThat(captor.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void completeValidatesObjectAndEnqueuesParseOnlyAfterDocumentInsert() {
        when(jsonCodec.write(any())).thenReturn("{}");
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        RagUploadSession session = session(content.length);
        String token = "upload-secret";
        session.setUploadTokenHash(sha256(token));
        when(uploads.findByIdForUpdate(501L)).thenReturn(session);
        when(storage.head(session.getObjectKey())).thenReturn(new ObjectStorageService.ObjectMetadata("\"etag\"", (long) content.length, null, "text/markdown"));
        when(storage.readBytes(session.getObjectKey())).thenReturn(content);
        when(parser.parse("md", "text/markdown", content)).thenReturn(new DocumentContentParser.ParsedDocument("hello world", "text/markdown"));
        when(ids.nextId()).thenReturn(601L);
        when(documents.insert(any())).thenReturn(1);
        when(uploads.markCompleted(501L)).thenReturn(1);
        when(documents.findById(101L, 601L)).thenAnswer(invocation -> {
            RagDocument document = new RagDocument();
            document.setDocumentId(601L);
            document.setProjectId(101L);
            return document;
        });

        RagDocument result = service.completeUpload(101L, 7L, 501L, token);

        assertThat(result.getDocumentId()).isEqualTo(601L);
        verify(documents).insert(any(RagDocument.class));
        verify(uploads).markCompleted(501L);
        verify(jobs).enqueue(org.mockito.ArgumentMatchers.eq("RAG_PARSE_DOCUMENT"), any(),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(101L), any());
    }

    @Test
    void completeDeletesInvalidTemporaryObject() {
        RagUploadSession session = session(20);
        String token = "upload-secret";
        session.setUploadTokenHash(sha256(token));
        when(uploads.findByIdForUpdate(501L)).thenReturn(session);
        when(storage.head(session.getObjectKey())).thenReturn(new ObjectStorageService.ObjectMetadata("etag", 3L, null, "text/markdown"));

        assertThatThrownBy(() -> service.completeUpload(101L, 7L, 501L, token))
                .isInstanceOf(BusinessException.class).hasMessageContaining("size");
        verify(storage).delete(session.getObjectKey());
    }

    private RagUploadSession session(long size) {
        RagUploadSession session = new RagUploadSession();
        session.setUploadId(501L);
        session.setProjectId(101L);
        session.setOwnerUserId(7L);
        session.setDocType("KNOWLEDGE_DOCUMENT");
        session.setTitle("Notes");
        session.setFileExtension("md");
        session.setDeclaredMimeType("text/markdown");
        session.setExpectedSize(size);
        session.setObjectKey("novels/101/rag/uploads/501.md");
        session.setUploadStatus("PENDING");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        return session;
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
