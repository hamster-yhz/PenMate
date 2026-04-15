package com.penmate.backend.application.rag;

import com.penmate.backend.application.rag.command.CreateRagDocumentCommand;
import com.penmate.backend.application.rag.command.OperateRagDocumentCommand;
import com.penmate.backend.domain.rag.repository.RagDocumentRepository;
import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.domain.shared.service.AuditService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RagApplicationService。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
@Service
public class RagApplicationService {

    private final RagDocumentRepository ragDocumentRepository;
    private final AuditService auditService;

    public RagApplicationService(RagDocumentRepository ragDocumentRepository,
                                 AuditService auditService) {
        this.ragDocumentRepository = ragDocumentRepository;
        this.auditService = auditService;
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<RagDocument> listDocuments(Long projectId) {
        return ragDocumentRepository.findByProjectId(projectId);
    }

    /**
     * 创建业务数据。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public RagDocument createDocument(Long projectId, CreateRagDocumentCommand command, String traceId) {
        RagDocument document = new RagDocument();
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
            throw new IllegalArgumentException("Failed to create rag document");
        }
        writeAudit(traceId, command.operatorId(), "rag", "create-document", "rag_documents", String.valueOf(document.getId()), command.title(), 201);
        return document;
    }

    /**
     * 查询详情数据。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @return 出参：处理结果
     */
    public RagDocument getDocument(Long projectId, Long docId) {
        RagDocument document = ragDocumentRepository.findById(projectId, docId);
        if (document == null) {
            throw new IllegalArgumentException("Rag document not found");
        }
        return document;
    }

    /**
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void deleteDocument(Long projectId, Long docId, OperateRagDocumentCommand command, String traceId) {
        int affected = ragDocumentRepository.softDelete(projectId, docId);
        if (affected != 1) {
            throw new IllegalArgumentException("Rag document not found");
        }
        writeAudit(traceId, command.operatorId(), "rag", "delete-document", "rag_documents", String.valueOf(docId), null, 200);
    }

    /**
     * 查询详情数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public Map<String, String> getDocumentUploadUrl(Long projectId) {
        String objectKey = "novels/" + projectId + "/rag/" + UUID.randomUUID();
        return Map.of(
                "objectKey", objectKey,
                "uploadUrl", "https://object.local/upload/" + objectKey + "?token=" + UUID.randomUUID()
        );
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public RagDocument parseDocument(Long projectId, Long docId, OperateRagDocumentCommand command, String traceId) {
        getDocument(projectId, docId);
        int affected = ragDocumentRepository.updateStatuses(projectId, docId, "done", "pending");
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to parse rag document");
        }
        writeAudit(traceId, command.operatorId(), "rag", "parse-document", "rag_documents", String.valueOf(docId), null, 200);
        return getDocument(projectId, docId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public RagDocument embedDocument(Long projectId, Long docId, OperateRagDocumentCommand command, String traceId) {
        RagDocument current = getDocument(projectId, docId);
        String parseStatus = current.getParseStatus() == null ? "pending" : current.getParseStatus();
        if (!"done".equalsIgnoreCase(parseStatus)) {
            throw new IllegalArgumentException("Document parse not finished");
        }
        int affected = ragDocumentRepository.updateStatuses(projectId, docId, "done", "done");
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to embed rag document");
        }
        writeAudit(traceId, command.operatorId(), "rag", "embed-document", "rag_documents", String.valueOf(docId), null, 200);
        return getDocument(projectId, docId);
    }

    /**
     * 查询详情数据。
     *
     * @param projectId 入参：projectId
     * @param docId 入参：docId
     * @return 出参：处理结果
     */
    public Map<String, Object> getIndexStatus(Long projectId, Long docId) {
        RagDocument document = getDocument(projectId, docId);
        return Map.of(
                "docId", document.getId(),
                "parseStatus", document.getParseStatus(),
                "indexStatus", document.getIndexStatus()
        );
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<Map<String, Object>> listRetrievalLogs(Long projectId) {
        return List.of(
                Map.of("projectId", projectId, "message", "no retrieval logs yet")
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
        String finalTraceId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        auditService.write(finalTraceId, userId, module, action, resourceType, resourceId, requestJson, responseCode);
    }
}

