package com.penmate.backend.interfaces.api.rag;

import com.penmate.backend.application.rag.RagApplicationService;
import com.penmate.backend.application.rag.ProjectAiConfigurationService;
import com.penmate.backend.application.rag.command.CreateRagDocumentCommand;
import com.penmate.backend.application.rag.command.OperateRagDocumentCommand;
import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.rag.dto.CreateRagDocumentDto;
import com.penmate.backend.interfaces.api.rag.dto.UpdateProjectAiConfigurationDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RAG 文档与检索日志控制器�?
 * <p>负责知识文档的创建、解析、向量化、索引状态查询，以及检索日志查询接口�?/p>
 */
@RestController
@RequestMapping("/api/v1/novels/{projectId}/rag")
public class RagController {

    private final RagApplicationService ragApplicationService;
    private final ProjectAiConfigurationService projectAiConfigurationService;

    public RagController(RagApplicationService ragApplicationService,
                         ProjectAiConfigurationService projectAiConfigurationService) {
        this.ragApplicationService = ragApplicationService;
        this.projectAiConfigurationService = projectAiConfigurationService;
    }

    @GetMapping("/configuration")
    public ApiResponse<Map<String, Object>> getConfiguration(@PathVariable String projectId,
                                                              Authentication authentication,
                                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(configurationView(projectAiConfigurationService.get(
                requireLongId(projectId, "projectId"), actor(authentication))), traceId);
    }

    @org.springframework.web.bind.annotation.PutMapping("/configuration")
    public ApiResponse<Map<String, Object>> updateConfiguration(@PathVariable String projectId,
                                                                 Authentication authentication,
                                                                 @Valid @RequestBody UpdateProjectAiConfigurationDto dto,
                                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var request = new ProjectAiConfigurationService.UpdateRequest(
                optionalLongId(dto.getEmbeddingModelConfigId(), "embeddingModelConfigId"),
                dto.getStoryBibleRoutingMode(),
                optionalLongId(dto.getRouterModelConfigId(), "routerModelConfigId"),
                dto.getChunkTargetCharacters(), dto.getChunkOverlapCharacters(), dto.getChunkMaxCharacters(),
                dto.getRetrievalCandidates(), dto.getRetrievalTopK(), dto.getRetrievalMaxPerSource(),
                dto.getHnswEfSearch(), dto.getSimilarityThreshold());
        return ApiResponse.success(configurationView(projectAiConfigurationService.update(
                requireLongId(projectId, "projectId"), actor(authentication), request)), traceId);
    }

    @PostMapping("/rebuild")
    public ApiResponse<Map<String, Object>> rebuild(@PathVariable String projectId,
                                                     Authentication authentication,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var job = projectAiConfigurationService.requestRebuild(
                requireLongId(projectId, "projectId"), actor(authentication));
        return ApiResponse.success(Map.of("jobId", String.valueOf(job.getJobId()), "status", job.getStatus()), traceId);
    }

    /**
     * 查询项目知识库文档列表�?
     * <p><b>业务目的�?/b>返回项目下所�?RAG 文档元数据，供知识库管理页展示�?/p>
     * <p><b>流程主线�?/b>读取项目业务 ID -> 调用应用服务查询文档 -> 统一封装响应�?/p>
     * <p><b>关键调用�?/b>{@code ragApplicationService.listDocuments(projectId)}�?/p>
     * <p><b>ID 语义�?/b>projectId 为项目业�?ID�?/p>
     * <p><b>异常与分支：</b>项目不存在时返回业务异常�?/p>
     * <p><b>副作用：</b>无持久化写入�?/p>
     */
    @GetMapping("/documents")
    public ApiResponse<List<Map<String, Object>>> listDocuments(@PathVariable String projectId,
                                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.listDocuments(requireLongId(projectId, "projectId")).stream().map(this::toDocumentView).toList(), traceId);
    }

    /**
     * 创建知识文档记录�?
     * <p><b>业务目的�?/b>登记待解析文档的来源信息与原始对象存储引用，进入后续解析与向量化流程�?/p>
     * <p><b>流程主线�?/b>校验请求�?-> 组装 {@link CreateRagDocumentCommand} -> 调用应用服务创建文档 -> 返回文档信息�?/p>
     * <p><b>关键调用�?/b>{@code ragApplicationService.createDocument(projectId, command, traceId)}�?/p>
     * <p><b>ID 语义�?/b>projectId、operatorId 均为业务语义 ID�?/p>
     * <p><b>异常与分支：</b>文档类型非法、项目无权限或来源信息缺失时返回业务异常�?/p>
     * <p><b>副作用：</b>新增文档记录�?/p>
     */
    @PostMapping("/documents")
    public ApiResponse<Map<String, Object>> createDocument(@PathVariable String projectId,
                                                           @Valid @RequestBody CreateRagDocumentDto dto,
                                                           @RequestParam("operatorId") String operatorId,
                                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        CreateRagDocumentCommand command = new CreateRagDocumentCommand(
                dto.getDocType(),
                dto.getTitle(),
                dto.getSourceRef(),
                dto.getOriginObjectKey(),
                dto.getOriginEtag(),
                dto.getMimeType(),
                requireLongId(operatorId, "operatorId")
        );
        return ApiResponse.success(toDocumentView(ragApplicationService.createDocument(requireLongId(projectId, "projectId"), command, traceId)), traceId);
    }

    /**
     * 查询文档详情�?
     * <p><b>业务目的�?/b>返回单个文档的状态、来源与索引相关信息，支持文档详情页展示�?/p>
     * <p><b>流程主线�?/b>接收文档业务 ID -> 调用应用服务查询详情 -> 封装响应�?/p>
     * <p><b>关键调用�?/b>{@code ragApplicationService.getDocument(projectId, documentId)}�?/p>
     * <p><b>ID 语义�?/b>projectId、documentId 均为业务语义 ID�?/p>
     * <p><b>异常与分支：</b>文档不存在或不属于项目时返回业务异常�?/p>
     * <p><b>副作用：</b>无持久化写入�?/p>
     */
    @GetMapping("/documents/{documentId}")
    public ApiResponse<Map<String, Object>> getDocument(@PathVariable String projectId,
                                                        @PathVariable String documentId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(toDocumentView(ragApplicationService.getDocument(requireLongId(projectId, "projectId"), requireLongId(documentId, "documentId"))), traceId);
    }

    /**
     * 删除知识文档�?
     * <p><b>业务目的�?/b>移除错误或过期文档，避免其继续参与检索与生成�?/p>
     * <p><b>流程主线�?/b>组装 {@link OperateRagDocumentCommand} -> 调用应用服务执行删除 -> 返回确认结果�?/p>
     * <p><b>关键调用�?/b>{@code ragApplicationService.deleteDocument(projectId, documentId, command, traceId)}�?/p>
     * <p><b>ID 语义�?/b>projectId、documentId、operatorId 均为业务语义 ID�?/p>
     * <p><b>异常与分支：</b>文档不存在、状态不可删或权限不足时返回业务异常�?/p>
     * <p><b>副作用：</b>删除文档元数据及关联索引数据�?/p>
     */
    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<String> deleteDocument(@PathVariable String projectId,
                                              @PathVariable String documentId,
                                              @RequestParam("operatorId") String operatorId,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        OperateRagDocumentCommand command = new OperateRagDocumentCommand(requireLongId(operatorId, "operatorId"));
        ragApplicationService.deleteDocument(requireLongId(projectId, "projectId"), requireLongId(documentId, "documentId"), command, traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 获取文档上传地址�?
     * <p><b>业务目的�?/b>为前端直传文档到对象存储生成上传 URL 与必要参数�?/p>
     * <p><b>流程主线�?/b>读取项目业务 ID -> 调用应用服务申请上传地址 -> 返回地址信息�?/p>
     * <p><b>关键调用�?/b>{@code ragApplicationService.getDocumentUploadUrl(projectId)}�?/p>
     * <p><b>ID 语义�?/b>projectId 为项目业�?ID�?/p>
     * <p><b>异常与分支：</b>存储服务不可用时返回业务异常�?/p>
     * <p><b>副作用：</b>可能创建短时上传凭证�?/p>
     */
    @PostMapping("/documents/upload-url")
    public ApiResponse<Map<String, String>> getUploadUrl(@PathVariable String projectId,
                                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.getDocumentUploadUrl(requireLongId(projectId, "projectId")), traceId);
    }

    /**
     * 触发文档解析�?
     * <p><b>业务目的�?/b>将原始文档转换为可切分文本，为后续向量化建立输入�?/p>
     * <p><b>流程主线�?/b>组装操作命令 -> 调用应用服务执行解析 -> 返回最新文档状态�?/p>
     * <p><b>关键调用�?/b>{@code ragApplicationService.parseDocument(projectId, documentId, command, traceId)}�?/p>
     * <p><b>ID 语义�?/b>projectId、documentId、operatorId 均为业务语义 ID�?/p>
     * <p><b>异常与分支：</b>文档状态不允许解析或解析失败时返回业务异常�?/p>
     * <p><b>副作用：</b>更新文档解析状态并写入解析产物�?/p>
     */
    @PostMapping("/documents/{documentId}/parse")
    public ApiResponse<Map<String, Object>> parseDocument(@PathVariable String projectId,
                                                          @PathVariable String documentId,
                                                          @RequestParam("operatorId") String operatorId,
                                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        OperateRagDocumentCommand command = new OperateRagDocumentCommand(requireLongId(operatorId, "operatorId"));
        return ApiResponse.success(toDocumentView(ragApplicationService.parseDocument(requireLongId(projectId, "projectId"), requireLongId(documentId, "documentId"), command, traceId)), traceId);
    }

    /**
     * 触发文档向量化�?
     * <p><b>业务目的�?/b>对已解析文本执行 embedding，建立向量索引以支持检索增强�?/p>
     * <p><b>流程主线�?/b>组装操作命令 -> 调用应用服务执行向量�?-> 返回更新后的文档状态�?/p>
     * <p><b>关键调用�?/b>{@code ragApplicationService.embedDocument(projectId, documentId, command, traceId)}�?/p>
     * <p><b>ID 语义�?/b>projectId、documentId、operatorId 均为业务语义 ID�?/p>
     * <p><b>异常与分支：</b>解析未完成、文档不可用或向量服务失败时返回业务异常�?/p>
     * <p><b>副作用：</b>写入向量索引并更新文档索引状态�?/p>
     */
    @PostMapping("/documents/{documentId}/embed")
    public ApiResponse<Map<String, Object>> embedDocument(@PathVariable String projectId,
                                                          @PathVariable String documentId,
                                                          @RequestParam("operatorId") String operatorId,
                                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        OperateRagDocumentCommand command = new OperateRagDocumentCommand(requireLongId(operatorId, "operatorId"));
        return ApiResponse.success(toDocumentView(ragApplicationService.embedDocument(requireLongId(projectId, "projectId"), requireLongId(documentId, "documentId"), command, traceId)), traceId);
    }

    /**
     * 查询文档索引状态�?
     * <p><b>业务目的�?/b>返回解析/向量化阶段状态与统计信息，便于前端展示处理进度�?/p>
     * <p><b>流程主线�?/b>读取文档标识 -> 调用应用服务获取索引状�?-> 封装响应�?/p>
     * <p><b>关键调用�?/b>{@code ragApplicationService.getIndexStatus(projectId, documentId)}�?/p>
     * <p><b>ID 语义�?/b>projectId、documentId 均为业务语义 ID�?/p>
     * <p><b>异常与分支：</b>文档不存在时返回业务异常�?/p>
     * <p><b>副作用：</b>无持久化写入�?/p>
     */
    @GetMapping("/documents/{documentId}/index-status")
    public ApiResponse<Map<String, Object>> indexStatus(@PathVariable String projectId,
                                                        @PathVariable String documentId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.getIndexStatus(requireLongId(projectId, "projectId"), requireLongId(documentId, "documentId")), traceId);
    }

    /**
     * 查询检索日志列表�?
     * <p><b>业务目的�?/b>返回项目检索调用记录，支持检索质量追踪与问题回放�?/p>
     * <p><b>流程主线�?/b>读取项目业务 ID -> 调用应用服务查询日志 -> 返回日志列表�?/p>
     * <p><b>关键调用�?/b>{@code ragApplicationService.listRetrievalLogs(projectId)}�?/p>
     * <p><b>ID 语义�?/b>projectId 为项目业�?ID�?/p>
     * <p><b>异常与分支：</b>项目不存在时返回业务异常�?/p>
     * <p><b>副作用：</b>无持久化写入�?/p>
     */
    @GetMapping("/retrieval-logs")
    public ApiResponse<List<Map<String, Object>>> retrievalLogs(@PathVariable String projectId,
                                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.listRetrievalLogs(requireLongId(projectId, "projectId")), traceId);
    }

    private Map<String, Object> toDocumentView(RagDocument ragDocument) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("documentId", ragDocument == null ? null : stringifyBusinessId(ragDocument.getDocumentId()));
        data.put("projectId", ragDocument == null ? null : stringifyBusinessId(ragDocument.getProjectId()));
        data.put("docType", ragDocument == null ? null : ragDocument.getDocType());
        data.put("title", ragDocument == null ? null : ragDocument.getTitle());
        data.put("sourceRef", ragDocument == null ? null : ragDocument.getSourceRef());
        data.put("originObjectKey", ragDocument == null ? null : ragDocument.getOriginObjectKey());
        data.put("originEtag", ragDocument == null ? null : ragDocument.getOriginEtag());
        data.put("mimeType", ragDocument == null ? null : ragDocument.getMimeType());
        data.put("parseStatus", ragDocument == null ? null : ragDocument.getParseStatus());
        data.put("indexStatus", ragDocument == null ? null : ragDocument.getIndexStatus());
        data.put("createdAt", ragDocument == null ? null : ragDocument.getCreatedAt());
        data.put("updatedAt", ragDocument == null ? null : ragDocument.getUpdatedAt());
        return data;
    }

    private Long requireLongId(String rawValue, String fieldName) {
        String normalized = Objects.requireNonNull(rawValue, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (!normalized.matches("^\\d+$")) {
            throw new IllegalArgumentException(fieldName + " must be a numeric string business id");
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid numeric string business id", ex);
        }
    }

    private Long optionalLongId(String rawValue, String fieldName) {
        return rawValue == null || rawValue.isBlank() ? null : requireLongId(rawValue, fieldName);
    }

    private Long actor(Authentication authentication) {
        if (authentication == null) throw com.penmate.backend.application.common.exception.BusinessException.unauthorized("Login required");
        return requireLongId(authentication.getName(), "principal userId");
    }

    private Map<String, Object> configurationView(com.penmate.backend.domain.rag.model.ProjectAiConfiguration value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectAiConfigId", String.valueOf(value.getProjectAiConfigId()));
        result.put("projectId", String.valueOf(value.getProjectId()));
        result.put("embeddingModelConfigId", value.getEmbeddingModelConfigId() == null ? null : String.valueOf(value.getEmbeddingModelConfigId()));
        result.put("storyBibleRoutingMode", value.getStoryBibleRoutingMode());
        result.put("routerModelConfigId", value.getRouterModelConfigId() == null ? null : String.valueOf(value.getRouterModelConfigId()));
        result.put("chunkTargetCharacters", value.getChunkTargetCharacters());
        result.put("chunkOverlapCharacters", value.getChunkOverlapCharacters());
        result.put("chunkMaxCharacters", value.getChunkMaxCharacters());
        result.put("retrievalCandidates", value.getRetrievalCandidates());
        result.put("retrievalTopK", value.getRetrievalTopK());
        result.put("retrievalMaxPerSource", value.getRetrievalMaxPerSource());
        result.put("hnswEfSearch", value.getHnswEfSearch());
        result.put("similarityThreshold", value.getSimilarityThreshold());
        result.put("indexStatus", value.getIndexStatus());
        result.put("activeIndexBuildId", value.getActiveIndexBuildId() == null ? null : String.valueOf(value.getActiveIndexBuildId()));
        result.put("lastErrorCode", value.getLastErrorCode());
        result.put("lastErrorMessage", value.getLastErrorMessage());
        return result;
    }

    private String stringifyBusinessId(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}



