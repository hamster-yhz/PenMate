package com.penmate.backend.application.rag;

import com.penmate.backend.application.rag.command.CreateRagDocumentCommand;
import com.penmate.backend.application.rag.command.OperateRagDocumentCommand;
import com.penmate.backend.domain.rag.model.RagRetrievalLog;
import com.penmate.backend.domain.rag.repository.RagDocumentRepository;
import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG 文档应用服务。
 * <p>负责项目知识文档的创建、删除、解析、向量化及索引状态查询。</p>
 */
@Service
@Slf4j
public class RagApplicationService {

    private final RagDocumentRepository ragDocumentRepository;
    private final BusinessIdGenerator businessIdGenerator;
    private final RagRetrievalService ragRetrievalService;
    private final String storageEndpoint;

    public RagApplicationService(RagDocumentRepository ragDocumentRepository,
                                 BusinessIdGenerator businessIdGenerator,
                                 RagRetrievalService ragRetrievalService,
                                 @Value("${penmate.storage.endpoint:http://localhost:9000}") String storageEndpoint) {
        this.ragDocumentRepository = ragDocumentRepository;
        this.businessIdGenerator = businessIdGenerator;
        this.ragRetrievalService = ragRetrievalService;
        this.storageEndpoint = storageEndpoint;
    }

    /**
     * 查询项目下的 RAG 文档列表。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<RagDocument> listDocuments(Long projectId) {
        List<RagDocument> documents = ragDocumentRepository.findByProjectId(projectId);
        log.info("查询RAG文档列表: projectId={}, count={}", projectId, documents.size());
        return documents;
    }

    /**
     * 新建 RAG 文档记录。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public RagDocument createDocument(Long projectId, CreateRagDocumentCommand command, String traceId) {
        log.info("创建RAG文档: projectId={}, title={}, docType={}, operatorId={}",
                projectId, command.title(), command.docType(), command.operatorId());
        RagDocument document = new RagDocument();
        document.setDocumentId(businessIdGenerator.nextId());
        document.setProjectId(projectId);
        document.setDocType(command.docType());
        document.setTitle(command.title());
        document.setSourceRef(command.sourceRef());
        document.setOriginObjectKey(command.originObjectKey());
        document.setOriginEtag(command.originEtag());
        document.setMimeType(command.mimeType());
        document.setParseStatus("pending");
        document.setIndexStatus("pending");
        int affected = ragDocumentRepository.insert(document);
        if (affected != 1) {
            log.error("创建RAG文档失败: projectId={}, title={}, reason=insert_failed", projectId, command.title());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create rag document");
        }
        writeAudit(traceId, command.operatorId(), "rag", "create-document", "rag_documents", String.valueOf(document.getDocumentId()), command.title(), 201);
        log.info("创建RAG文档成功: projectId={}, docId={}, title={}", projectId, document.getDocumentId(), document.getTitle());
        return document;
    }

    /**
     * 查询单个 RAG 文档详情。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @return 出参：处理结果
     */
    public RagDocument getDocument(Long projectId, Long docId) {
        log.info("查询RAG文档详情: projectId={}, docId={}", projectId, docId);
        RagDocument document = ragDocumentRepository.findById(projectId, docId);
        if (document == null) {
            log.warn("查询RAG文档详情失败: projectId={}, docId={}, reason=not_found", projectId, docId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Rag document not found");
        }
        log.info("查询RAG文档详情成功: projectId={}, docId={}, parseStatus={}, indexStatus={}",
                projectId, docId, document.getParseStatus(), document.getIndexStatus());
        return document;
    }

    /**
     * 删除指定 RAG 文档。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void deleteDocument(Long projectId, Long docId, OperateRagDocumentCommand command, String traceId) {
        log.info("删除RAG文档: projectId={}, docId={}, operatorId={}", projectId, docId, command.operatorId());
        int affected = ragDocumentRepository.softDelete(projectId, docId);
        if (affected != 1) {
            log.warn("删除RAG文档失败: projectId={}, docId={}, reason=not_found", projectId, docId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Rag document not found");
        }
        writeAudit(traceId, command.operatorId(), "rag", "delete-document", "rag_documents", String.valueOf(docId), null, 200);
        log.info("删除RAG文档成功: projectId={}, docId={}", projectId, docId);
    }

    /**
     * 获取文档上传地址。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public Map<String, String> getDocumentUploadUrl(Long projectId) {
        String objectKey = "novels/" + projectId + "/rag/" + UUID.randomUUID();
        log.info("生成RAG文档上传地址: projectId={}, objectKey={}", projectId, objectKey);
        return Map.of(
                "objectKey", objectKey,
                "uploadUrl", buildObjectUploadUrl(objectKey)
        );
    }

    private String buildObjectUploadUrl(String objectKey) {
        return normalizedStorageEndpoint() + "/upload/" + objectKey + "?token=" + UUID.randomUUID();
    }

    private String normalizedStorageEndpoint() {
        if (storageEndpoint.endsWith("/")) {
            return storageEndpoint.substring(0, storageEndpoint.length() - 1);
        }
        return storageEndpoint;
    }

    /**
     * 触发文档解析流程。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public RagDocument parseDocument(Long projectId, Long docId, OperateRagDocumentCommand command, String traceId) {
        log.info("触发RAG文档解析: projectId={}, docId={}, operatorId={}", projectId, docId, command.operatorId());
        getDocument(projectId, docId);
        int affected = ragDocumentRepository.updateStatuses(projectId, docId, "done", "pending");
        if (affected != 1) {
            log.error("触发RAG文档解析失败: projectId={}, docId={}, reason=update_failed", projectId, docId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to parse rag document");
        }
        writeAudit(traceId, command.operatorId(), "rag", "parse-document", "rag_documents", String.valueOf(docId), null, 200);
        log.info("触发RAG文档解析成功: projectId={}, docId={}", projectId, docId);
        return getDocument(projectId, docId);
    }

    /**
     * 触发文档向量化入库流程。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public RagDocument embedDocument(Long projectId, Long docId, OperateRagDocumentCommand command, String traceId) {
        log.info("触发RAG文档向量化: projectId={}, docId={}, operatorId={}", projectId, docId, command.operatorId());
        RagDocument current = getDocument(projectId, docId);
        String parseStatus = current.getParseStatus() == null ? "pending" : current.getParseStatus();
        if (!"done".equalsIgnoreCase(parseStatus)) {
            log.warn("触发RAG文档向量化失败: projectId={}, docId={}, parseStatus={}", projectId, docId, parseStatus);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Document parse not finished");
        }
        int affected = ragDocumentRepository.updateStatuses(projectId, docId, "done", "done");
        if (affected != 1) {
            log.error("触发RAG文档向量化失败: projectId={}, docId={}, reason=update_failed", projectId, docId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to embed rag document");
        }
        writeAudit(traceId, command.operatorId(), "rag", "embed-document", "rag_documents", String.valueOf(docId), null, 200);
        log.info("触发RAG文档向量化成功: projectId={}, docId={}", projectId, docId);
        return getDocument(projectId, docId);
    }

    /**
     * 查询文档解析/索引状态。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @return 出参：处理结果
     */
    public Map<String, Object> getIndexStatus(Long projectId, Long docId) {
        RagDocument document = getDocument(projectId, docId);
        log.info("查询RAG索引状态: projectId={}, docId={}, parseStatus={}, indexStatus={}",
                projectId, docId, document.getParseStatus(), document.getIndexStatus());
        return Map.of(
                "docId", document.getDocumentId(),
                "parseStatus", document.getParseStatus(),
                "indexStatus", document.getIndexStatus()
        );
    }

    /**
     * 查询项目检索日志（当前为占位实现）。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<Map<String, Object>> listRetrievalLogs(Long projectId) {
        List<Map<String, Object>> logs = ragRetrievalService.listRetrievalLogs(projectId)
                .stream()
                .map(this::toRetrievalLogView)
                .toList();
        log.info("查询RAG检索日志: projectId={}, count={}", projectId, logs.size());
        return logs;
    }

    private Map<String, Object> toRetrievalLogView(RagRetrievalLog log) {
        return Map.of(
                "id", log.getRetrievalLogId(),
                "projectId", log.getProjectId(),
                "runId", log.getRunId(),
                "queryText", log.getQueryText() == null ? "" : log.getQueryText(),
                "hitCount", log.getHitCount() == null ? 0 : log.getHitCount(),
                "sourcesJson", log.getSourcesJson() == null ? "[]" : log.getSourcesJson(),
                "latencyMs", log.getLatencyMs() == null ? 0 : log.getLatencyMs(),
                "adopted", log.getAdopted() != null && log.getAdopted(),
                "traceId", log.getTraceId() == null ? "" : log.getTraceId(),
                "createdAt", log.getCreatedAt() == null ? "" : log.getCreatedAt().toString()
        );
    }

    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        // 审计模块已移除
    }
}


