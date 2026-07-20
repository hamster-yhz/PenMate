package com.penmate.backend.application.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.ops.AsyncJobQueueService;
import com.penmate.backend.application.rag.command.CreateRagDocumentCommand;
import com.penmate.backend.application.rag.command.OperateRagDocumentCommand;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.domain.rag.model.RagRetrievalLog;
import com.penmate.backend.domain.rag.model.RagUploadSession;
import com.penmate.backend.domain.rag.repository.RagDocumentRepository;
import com.penmate.backend.domain.rag.repository.RagUploadSessionRepository;
import com.penmate.backend.domain.rag.service.DocumentContentParser;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class RagApplicationService {
    private static final Set<String> EXTENSIONS = Set.of("txt", "md", "markdown", "html", "htm");

    private final RagDocumentRepository documents;
    private final RagUploadSessionRepository uploads;
    private final NovelGateway novels;
    private final BusinessIdGenerator ids;
    private final RagRetrievalService retrieval;
    private final ObjectStorageService storage;
    private final DocumentContentParser parser;
    private final AsyncJobQueueService jobs;
    private final ObjectMapper objectMapper;
    @Value("${penmate.indexing.max-upload-bytes:10485760}")
    private long maxUploadBytes = 10485760L;
    @Value("${penmate.storage.presign-expire-minutes:15}")
    private long uploadTtlMinutes = 15L;

    public RagApplicationService(RagDocumentRepository documents,
                                 RagUploadSessionRepository uploads,
                                 NovelGateway novels,
                                 BusinessIdGenerator ids,
                                 RagRetrievalService retrieval,
                                 ObjectStorageService storage,
                                 DocumentContentParser parser,
                                 AsyncJobQueueService jobs,
                                 ObjectMapper objectMapper) {
        this.documents = documents;
        this.uploads = uploads;
        this.novels = novels;
        this.ids = ids;
        this.retrieval = retrieval;
        this.storage = storage;
        this.parser = parser;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
    }

    public List<RagDocument> listDocuments(Long projectId, Long actorUserId) {
        requireOwner(projectId, actorUserId);
        return documents.findByProjectId(projectId);
    }

    public RagDocument getDocument(Long projectId, Long docId, Long actorUserId) {
        requireOwner(projectId, actorUserId);
        return requireDocument(projectId, docId);
    }

    @Transactional
    public UploadInitialization initializeUpload(Long projectId, Long actorUserId, UploadRequest request) {
        requireOwner(projectId, actorUserId);
        Objects.requireNonNull(request, "request");
        String filename = requireText(request.filename(), "filename");
        String extension = extension(filename);
        String mimeType = normalizeMime(request.mimeType());
        validateUploadDeclaration(extension, mimeType, request.size());
        String title = request.title() == null || request.title().isBlank()
                ? stripExtension(filename) : request.title().strip();
        if (title.length() > 200) throw BusinessException.badRequest("Document title is too long");

        long uploadId = ids.nextId();
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        String objectKey = "novels/%d/rag/uploads/%d.%s".formatted(projectId, uploadId, extension);
        RagUploadSession session = new RagUploadSession();
        session.setUploadId(uploadId);
        session.setProjectId(projectId);
        session.setOwnerUserId(actorUserId);
        session.setDocType("KNOWLEDGE_DOCUMENT");
        session.setTitle(title);
        session.setOriginalFilename(filename);
        session.setFileExtension(extension);
        session.setDeclaredMimeType(mimeType);
        session.setExpectedSize(request.size());
        session.setExpectedChecksum(normalizeChecksum(request.sha256()));
        session.setObjectKey(objectKey);
        session.setUploadTokenHash(sha256(token.getBytes(StandardCharsets.UTF_8)));
        session.setUploadStatus("PENDING");
        session.setExpiresAt(Instant.now().plus(Math.max(1, uploadTtlMinutes), ChronoUnit.MINUTES));
        if (uploads.insert(session) != 1) throw BusinessException.of("Failed to initialize document upload");
        return new UploadInitialization(String.valueOf(uploadId), token, objectKey,
                storage.buildUploadUrl(objectKey, mimeType), session.getExpiresAt());
    }

    @Transactional
    public RagDocument completeUpload(Long projectId, Long actorUserId, Long uploadId, String uploadToken) {
        requireOwner(projectId, actorUserId);
        RagUploadSession session = uploads.findByIdForUpdate(uploadId);
        if (session == null || !Objects.equals(session.getProjectId(), projectId)
                || !Objects.equals(session.getOwnerUserId(), actorUserId)) {
            throw BusinessException.notFound("Upload session not found");
        }
        if (!"PENDING".equals(session.getUploadStatus())) throw BusinessException.conflict("Upload session is not pending");
        if (session.getExpiresAt().isBefore(Instant.now())) throw BusinessException.conflict("Upload session has expired");
        verifyToken(session, uploadToken);

        try {
            ObjectStorageService.ObjectMetadata metadata = storage.head(session.getObjectKey());
            if (!Objects.equals(metadata.size(), session.getExpectedSize()) || metadata.size() <= 0 || metadata.size() > maxUploadBytes) {
                throw BusinessException.badRequest("Uploaded object size does not match the initialized upload");
            }
            if (!normalizeMime(metadata.contentType()).equals(session.getDeclaredMimeType())) {
                throw BusinessException.badRequest("Uploaded object content type does not match the initialized upload");
            }
            byte[] content = storage.readBytes(session.getObjectKey());
            String contentChecksum = sha256(content);
            if (session.getExpectedChecksum() != null && !session.getExpectedChecksum().equals(contentChecksum)) {
                throw BusinessException.badRequest("Uploaded object checksum does not match");
            }
            DocumentContentParser.ParsedDocument parsed = parser.parse(
                    session.getFileExtension(), session.getDeclaredMimeType(), content);

            RagDocument document = new RagDocument();
            document.setDocumentId(ids.nextId());
            document.setProjectId(projectId);
            document.setDocType(session.getDocType());
            document.setTitle(session.getTitle());
            document.setOriginObjectKey(session.getObjectKey());
            document.setOriginEtag(normalizeEtag(metadata.etag()));
            document.setOriginChecksum(contentChecksum);
            document.setOriginSize(metadata.size());
            document.setFileExtension(session.getFileExtension());
            document.setMimeType(parsed.detectedMimeType());
            document.setSourceRevision(1L);
            document.setParseStatus("PENDING");
            document.setIndexStatus("PENDING");
            if (documents.insert(document) != 1 || uploads.markCompleted(uploadId) != 1) {
                throw BusinessException.of("Failed to complete document upload");
            }
            enqueueParse(document, actorUserId);
            return requireDocument(projectId, document.getDocumentId());
        } catch (BusinessException exception) {
            deleteInvalidObject(session.getObjectKey());
            throw exception;
        }
    }

    @Transactional
    public void deleteDocument(Long projectId, Long docId, Long actorUserId) {
        requireOwner(projectId, actorUserId);
        RagDocument document = requireDocument(projectId, docId);
        if (documents.softDelete(projectId, docId) != 1) throw BusinessException.notFound("Rag document not found");
        jobs.enqueue("RAG_REINDEX_SOURCE", "rag:document:%d:delete:%d".formatted(docId, document.getSourceRevision()),
                actorUserId, projectId, json(Map.of("projectId", projectId, "documentId", docId,
                        "sourceRevision", document.getSourceRevision(), "operation", "DELETE")));
    }

    @Transactional
    public RagDocument requestParse(Long projectId, Long docId, Long actorUserId) {
        requireOwner(projectId, actorUserId);
        RagDocument document = requireDocument(projectId, docId);
        documents.updateProcessingState(projectId, docId, "PENDING", "PENDING", null, null);
        enqueueParse(document, actorUserId);
        return requireDocument(projectId, docId);
    }

    @Transactional
    public RagDocument requestEmbedding(Long projectId, Long docId, Long actorUserId) {
        requireOwner(projectId, actorUserId);
        RagDocument document = requireDocument(projectId, docId);
        if (!"DONE".equalsIgnoreCase(document.getParseStatus())) throw BusinessException.conflict("Document parse not finished");
        jobs.enqueue("RAG_EMBED_DOCUMENT", "rag:document:%d:embed:%d".formatted(docId, document.getSourceRevision()),
                actorUserId, projectId, documentPayload(document));
        documents.updateProcessingState(projectId, docId, "DONE", "PENDING", null, null);
        return requireDocument(projectId, docId);
    }

    public Map<String, Object> getIndexStatus(Long projectId, Long docId, Long actorUserId) {
        RagDocument document = getDocument(projectId, docId, actorUserId);
        return Map.of("documentId", String.valueOf(document.getDocumentId()),
                "parseStatus", document.getParseStatus(), "indexStatus", document.getIndexStatus());
    }

    public List<Map<String, Object>> listRetrievalLogs(Long projectId, Long actorUserId) {
        requireOwner(projectId, actorUserId);
        return retrieval.listRetrievalLogs(projectId).stream().map(this::toRetrievalLogView).toList();
    }

    // Worker entrypoint: validates the immutable source again before parsing.
    public DocumentContentParser.ParsedDocument loadAndParse(Long projectId, Long documentId, long sourceRevision) {
        RagDocument document = requireDocument(projectId, documentId);
        if (!Objects.equals(document.getSourceRevision(), sourceRevision)) {
            throw BusinessException.conflict("Document revision is stale");
        }
        return parser.parse(document.getFileExtension(), document.getMimeType(), storage.readBytes(document.getOriginObjectKey()));
    }

    public void updateProcessingState(Long projectId, Long documentId, String parseStatus, String indexStatus,
                                      String errorCode, String errorMessage) {
        documents.updateProcessingState(projectId, documentId, parseStatus, indexStatus, errorCode, errorMessage);
    }

    private void enqueueParse(RagDocument document, Long ownerUserId) {
        jobs.enqueue("RAG_PARSE_DOCUMENT",
                "rag:document:%d:parse:%d".formatted(document.getDocumentId(), document.getSourceRevision()),
                ownerUserId, document.getProjectId(), documentPayload(document));
    }

    private String documentPayload(RagDocument document) {
        return json(Map.of("projectId", document.getProjectId(), "documentId", document.getDocumentId(),
                "sourceRevision", document.getSourceRevision()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize async job payload", exception);
        }
    }

    private void validateUploadDeclaration(String extension, String mimeType, Long size) {
        if (!EXTENSIONS.contains(extension)) throw BusinessException.badRequest("Only TXT, Markdown, and HTML documents are supported");
        if (size == null || size <= 0 || size > maxUploadBytes) throw BusinessException.badRequest("Document size must be between 1 byte and 10 MiB");
        boolean html = Set.of("html", "htm").contains(extension);
        boolean mimeMatches = html ? Set.of("text/html", "application/xhtml+xml").contains(mimeType)
                : Set.of("text/plain", "text/markdown", "text/x-markdown").contains(mimeType);
        if (!mimeMatches) throw BusinessException.badRequest("Filename extension and MIME type do not match");
    }

    private void verifyToken(RagUploadSession session, String token) {
        if (token == null || !MessageDigest.isEqual(
                session.getUploadTokenHash().getBytes(StandardCharsets.US_ASCII),
                sha256(token.getBytes(StandardCharsets.UTF_8)).getBytes(StandardCharsets.US_ASCII))) {
            throw BusinessException.forbidden("Invalid upload token");
        }
    }

    private void deleteInvalidObject(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException ignored) {
            // The rejected object is unreachable and can be removed by storage lifecycle policy.
        }
    }

    private RagDocument requireDocument(Long projectId, Long docId) {
        RagDocument document = documents.findById(projectId, docId);
        if (document == null) throw BusinessException.notFound("Rag document not found");
        return document;
    }

    private void requireOwner(Long projectId, Long actorUserId) {
        NovelProject project = novels.findProjectById(projectId);
        if (project == null || !Objects.equals(project.getOwnerUserId(), actorUserId)) {
            throw BusinessException.notFound("Novel project not found");
        }
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 1 || index == filename.length() - 1) throw BusinessException.badRequest("Filename must include a supported extension");
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return (index <= 0 ? filename : filename.substring(0, index)).strip();
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw BusinessException.badRequest(field + " is required");
        return value.strip();
    }

    private String normalizeMime(String value) {
        if (value == null) return "";
        return value.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeChecksum(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) throw BusinessException.badRequest("sha256 must be 64 lowercase hexadecimal characters");
        return normalized;
    }

    private String normalizeEtag(String value) {
        return value == null ? null : value.replace("\"", "").strip();
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Map<String, Object> toRetrievalLogView(RagRetrievalLog log) {
        return Map.of("id", String.valueOf(log.getRetrievalLogId()), "projectId", String.valueOf(log.getProjectId()),
                "runId", log.getRunId() == null ? "" : String.valueOf(log.getRunId()),
                "queryText", log.getQueryText() == null ? "" : log.getQueryText(),
                "hitCount", log.getHitCount() == null ? 0 : log.getHitCount(),
                "sourcesJson", log.getSourcesJson() == null ? "[]" : log.getSourcesJson(),
                "latencyMs", log.getLatencyMs() == null ? 0 : log.getLatencyMs(),
                "adopted", Boolean.TRUE.equals(log.getAdopted()),
                "traceId", log.getTraceId() == null ? "" : log.getTraceId());
    }

    public record UploadRequest(String filename, String title, String mimeType, Long size, String sha256) {
    }

    public record UploadInitialization(String uploadId, String uploadToken, String objectKey,
                                       String uploadUrl, Instant expiresAt) {
    }

    // Compatibility methods retained for application tests and internal callers during API migration.
    List<RagDocument> listDocuments(Long projectId) { return documents.findByProjectId(projectId); }
    RagDocument getDocument(Long projectId, Long docId) { return requireDocument(projectId, docId); }
    RagDocument createDocument(Long projectId, CreateRagDocumentCommand command, String traceId) {
        RagDocument document = new RagDocument();
        document.setDocumentId(ids.nextId());
        document.setProjectId(projectId);
        document.setDocType(command.docType());
        document.setTitle(command.title());
        document.setSourceRef(command.sourceRef());
        document.setOriginObjectKey(command.originObjectKey());
        document.setOriginEtag(command.originEtag());
        document.setFileExtension("md");
        document.setMimeType(command.mimeType());
        document.setSourceRevision(1L);
        document.setParseStatus("PENDING");
        document.setIndexStatus("PENDING");
        if (documents.insert(document) != 1) throw BusinessException.of("Failed to create rag document");
        return document;
    }
    void deleteDocument(Long projectId, Long docId, OperateRagDocumentCommand command, String traceId) {
        if (documents.softDelete(projectId, docId) != 1) throw BusinessException.notFound("Rag document not found");
    }
    RagDocument parseDocument(Long projectId, Long docId, OperateRagDocumentCommand command, String traceId) {
        requireDocument(projectId, docId);
        if (documents.updateStatuses(projectId, docId, "done", "pending") != 1) throw BusinessException.of("Failed to parse rag document");
        return requireDocument(projectId, docId);
    }
    RagDocument embedDocument(Long projectId, Long docId, OperateRagDocumentCommand command, String traceId) {
        RagDocument current = requireDocument(projectId, docId);
        if (!"done".equalsIgnoreCase(current.getParseStatus())) throw BusinessException.of("Document parse not finished");
        if (documents.updateStatuses(projectId, docId, "done", "done") != 1) throw BusinessException.of("Failed to embed rag document");
        return requireDocument(projectId, docId);
    }
    Map<String, Object> getIndexStatus(Long projectId, Long docId) {
        RagDocument document = requireDocument(projectId, docId);
        return Map.of("docId", document.getDocumentId(), "parseStatus", document.getParseStatus(), "indexStatus", document.getIndexStatus());
    }
    List<Map<String, Object>> listRetrievalLogs(Long projectId) {
        return retrieval.listRetrievalLogs(projectId).stream().map(log -> Map.<String, Object>of(
                "id", log.getRetrievalLogId(), "projectId", log.getProjectId(), "runId", log.getRunId(),
                "queryText", log.getQueryText() == null ? "" : log.getQueryText(),
                "hitCount", log.getHitCount() == null ? 0 : log.getHitCount(),
                "sourcesJson", log.getSourcesJson() == null ? "[]" : log.getSourcesJson(),
                "latencyMs", log.getLatencyMs() == null ? 0 : log.getLatencyMs(),
                "adopted", Boolean.TRUE.equals(log.getAdopted()),
                "traceId", log.getTraceId() == null ? "" : log.getTraceId())).toList();
    }
}
