package com.penmate.backend.interfaces.api.rag;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.rag.ProjectAiConfigurationService;
import com.penmate.backend.application.rag.RagApplicationService;
import com.penmate.backend.domain.rag.model.ProjectAiConfiguration;
import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.rag.dto.CompleteRagUploadDto;
import com.penmate.backend.interfaces.api.rag.dto.InitializeRagUploadDto;
import com.penmate.backend.interfaces.api.rag.dto.UpdateProjectAiConfigurationDto;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/novels/{projectId}/rag")
public class RagController {
    private final RagApplicationService rag;
    private final ProjectAiConfigurationService configurations;

    public RagController(RagApplicationService rag, ProjectAiConfigurationService configurations) {
        this.rag = rag;
        this.configurations = configurations;
    }

    @GetMapping("/configuration")
    public ApiResponse<Map<String, Object>> getConfiguration(@PathVariable String projectId,
                                                              Authentication authentication,
                                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(configurationView(configurations.get(id(projectId), actor(authentication))), traceId);
    }

    @PutMapping("/configuration")
    public ApiResponse<Map<String, Object>> updateConfiguration(@PathVariable String projectId,
                                                                 Authentication authentication,
                                                                 @Valid @RequestBody UpdateProjectAiConfigurationDto dto,
                                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var request = new ProjectAiConfigurationService.UpdateRequest(
                optionalId(dto.getCreativeModelConfigId()), optionalId(dto.getEmbeddingModelConfigId()), dto.getStoryBibleRoutingMode(),
                optionalId(dto.getRouterModelConfigId()), dto.getChunkTargetCharacters(),
                dto.getChunkOverlapCharacters(), dto.getChunkMaxCharacters(), dto.getRetrievalCandidates(),
                dto.getRetrievalTopK(), dto.getRetrievalMaxPerSource(), dto.getHnswEfSearch(), dto.getSimilarityThreshold());
        return ApiResponse.success(configurationView(configurations.update(id(projectId), actor(authentication), request)), traceId);
    }

    @PostMapping("/rebuild")
    public ApiResponse<Map<String, Object>> rebuild(@PathVariable String projectId, Authentication authentication,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var job = configurations.requestRebuild(id(projectId), actor(authentication));
        return ApiResponse.success(Map.of("jobId", String.valueOf(job.getJobId()), "status", job.getStatus()), traceId);
    }

    @GetMapping("/documents")
    public ApiResponse<List<Map<String, Object>>> listDocuments(@PathVariable String projectId,
                                                                 Authentication authentication,
                                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(rag.listDocuments(id(projectId), actor(authentication)).stream().map(this::documentView).toList(), traceId);
    }

    @PostMapping("/documents/uploads")
    public ApiResponse<RagApplicationService.UploadInitialization> initializeUpload(
            @PathVariable String projectId, Authentication authentication,
            @Valid @RequestBody InitializeRagUploadDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        var request = new RagApplicationService.UploadRequest(dto.getFilename(), dto.getTitle(), dto.getMimeType(), dto.getSize(), dto.getSha256());
        return ApiResponse.success(rag.initializeUpload(id(projectId), actor(authentication), request), traceId);
    }

    @PostMapping("/documents/uploads/{uploadId}/complete")
    public ApiResponse<Map<String, Object>> completeUpload(@PathVariable String projectId,
                                                            @PathVariable String uploadId,
                                                            Authentication authentication,
                                                            @Valid @RequestBody CompleteRagUploadDto dto,
                                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(documentView(rag.completeUpload(id(projectId), actor(authentication), id(uploadId), dto.getUploadToken())), traceId);
    }

    @GetMapping("/documents/{documentId}")
    public ApiResponse<Map<String, Object>> getDocument(@PathVariable String projectId, @PathVariable String documentId,
                                                         Authentication authentication,
                                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(documentView(rag.getDocument(id(projectId), id(documentId), actor(authentication))), traceId);
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<String> deleteDocument(@PathVariable String projectId, @PathVariable String documentId,
                                               Authentication authentication,
                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        rag.deleteDocument(id(projectId), id(documentId), actor(authentication));
        return ApiResponse.success("deleted", traceId);
    }

    @PostMapping("/documents/{documentId}/parse")
    public ApiResponse<Map<String, Object>> parseDocument(@PathVariable String projectId, @PathVariable String documentId,
                                                           Authentication authentication,
                                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(documentView(rag.requestParse(id(projectId), id(documentId), actor(authentication))), traceId);
    }

    @PostMapping("/documents/{documentId}/embed")
    public ApiResponse<Map<String, Object>> embedDocument(@PathVariable String projectId, @PathVariable String documentId,
                                                           Authentication authentication,
                                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(documentView(rag.requestEmbedding(id(projectId), id(documentId), actor(authentication))), traceId);
    }

    @GetMapping("/documents/{documentId}/index-status")
    public ApiResponse<Map<String, Object>> indexStatus(@PathVariable String projectId, @PathVariable String documentId,
                                                         Authentication authentication,
                                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(rag.getIndexStatus(id(projectId), id(documentId), actor(authentication)), traceId);
    }

    @GetMapping("/retrieval-logs")
    public ApiResponse<List<Map<String, Object>>> retrievalLogs(@PathVariable String projectId,
                                                                 Authentication authentication,
                                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(rag.listRetrievalLogs(id(projectId), actor(authentication)), traceId);
    }

    private Map<String, Object> documentView(RagDocument value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", string(value.getDocumentId()));
        result.put("projectId", string(value.getProjectId()));
        result.put("docType", value.getDocType());
        result.put("title", value.getTitle());
        result.put("sourceRef", value.getSourceRef());
        result.put("originObjectKey", value.getOriginObjectKey());
        result.put("originEtag", value.getOriginEtag());
        result.put("originChecksum", value.getOriginChecksum());
        result.put("originSize", value.getOriginSize());
        result.put("fileExtension", value.getFileExtension());
        result.put("mimeType", value.getMimeType());
        result.put("sourceRevision", value.getSourceRevision());
        result.put("parseStatus", value.getParseStatus());
        result.put("indexStatus", value.getIndexStatus());
        result.put("lastErrorCode", value.getLastErrorCode());
        result.put("lastErrorMessage", value.getLastErrorMessage());
        result.put("createdAt", value.getCreatedAt());
        result.put("updatedAt", value.getUpdatedAt());
        return result;
    }

    private Map<String, Object> configurationView(ProjectAiConfiguration value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectAiConfigId", string(value.getProjectAiConfigId()));
        result.put("projectId", string(value.getProjectId()));
        result.put("creativeModelConfigId", string(value.getCreativeModelConfigId()));
        result.put("embeddingModelConfigId", string(value.getEmbeddingModelConfigId()));
        result.put("storyBibleRoutingMode", value.getStoryBibleRoutingMode());
        result.put("routerModelConfigId", string(value.getRouterModelConfigId()));
        result.put("chunkTargetCharacters", value.getChunkTargetCharacters());
        result.put("chunkOverlapCharacters", value.getChunkOverlapCharacters());
        result.put("chunkMaxCharacters", value.getChunkMaxCharacters());
        result.put("retrievalCandidates", value.getRetrievalCandidates());
        result.put("retrievalTopK", value.getRetrievalTopK());
        result.put("retrievalMaxPerSource", value.getRetrievalMaxPerSource());
        result.put("hnswEfSearch", value.getHnswEfSearch());
        result.put("similarityThreshold", value.getSimilarityThreshold());
        result.put("indexStatus", value.getIndexStatus());
        result.put("activeIndexBuildId", string(value.getActiveIndexBuildId()));
        result.put("lastErrorCode", value.getLastErrorCode());
        result.put("lastErrorMessage", value.getLastErrorMessage());
        return result;
    }

    private Long actor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw BusinessException.unauthorized("Login required");
        return id(authentication.getName());
    }

    private Long optionalId(String value) { return value == null || value.isBlank() ? null : id(value); }
    private Long id(String value) {
        if (value == null || !value.matches("\\d+")) throw BusinessException.badRequest("Business ID must be numeric");
        try { return Long.valueOf(value); }
        catch (NumberFormatException exception) { throw BusinessException.badRequest("Business ID is out of range"); }
    }
    private String string(Long value) { return value == null ? null : String.valueOf(value); }
}
