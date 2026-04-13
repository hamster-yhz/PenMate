package com.penmate.backend.interfaces.api.rag;

import com.penmate.backend.application.rag.RagApplicationService;
import com.penmate.backend.application.rag.command.CreateRagDocumentCommand;
import com.penmate.backend.application.rag.command.OperateRagDocumentCommand;
import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.rag.dto.CreateRagDocumentDto;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/novels/{projectId}/rag")
public class RagController {

    private final RagApplicationService ragApplicationService;

    public RagController(RagApplicationService ragApplicationService) {
        this.ragApplicationService = ragApplicationService;
    }

    @GetMapping("/documents")
    public ApiResponse<List<RagDocument>> listDocuments(@PathVariable Long projectId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.listDocuments(projectId), traceId);
    }

    @PostMapping("/documents")
    public ApiResponse<RagDocument> createDocument(@PathVariable Long projectId,
                                                   @Valid @RequestBody CreateRagDocumentDto dto,
                                                   @RequestParam("operatorId") Long operatorId,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        CreateRagDocumentCommand command = new CreateRagDocumentCommand(
                dto.getDocType(),
                dto.getTitle(),
                dto.getSourceRef(),
                dto.getOriginObjectKey(),
                dto.getOriginEtag(),
                dto.getMimeType(),
                operatorId
        );
        return ApiResponse.success(ragApplicationService.createDocument(projectId, command, traceId), traceId);
    }

    @GetMapping("/documents/{docId}")
    public ApiResponse<RagDocument> getDocument(@PathVariable Long projectId,
                                                @PathVariable Long docId,
                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.getDocument(projectId, docId), traceId);
    }

    @DeleteMapping("/documents/{docId}")
    public ApiResponse<String> deleteDocument(@PathVariable Long projectId,
                                              @PathVariable Long docId,
                                              @RequestParam("operatorId") Long operatorId,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        OperateRagDocumentCommand command = new OperateRagDocumentCommand(operatorId);
        ragApplicationService.deleteDocument(projectId, docId, command, traceId);
        return ApiResponse.success("deleted", traceId);
    }

    @PostMapping("/documents/upload-url")
    public ApiResponse<Map<String, String>> getUploadUrl(@PathVariable Long projectId,
                                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.getDocumentUploadUrl(projectId), traceId);
    }

    @PostMapping("/documents/{docId}/parse")
    public ApiResponse<RagDocument> parseDocument(@PathVariable Long projectId,
                                                  @PathVariable Long docId,
                                                  @RequestParam("operatorId") Long operatorId,
                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        OperateRagDocumentCommand command = new OperateRagDocumentCommand(operatorId);
        return ApiResponse.success(ragApplicationService.parseDocument(projectId, docId, command, traceId), traceId);
    }

    @PostMapping("/documents/{docId}/embed")
    public ApiResponse<RagDocument> embedDocument(@PathVariable Long projectId,
                                                  @PathVariable Long docId,
                                                  @RequestParam("operatorId") Long operatorId,
                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        OperateRagDocumentCommand command = new OperateRagDocumentCommand(operatorId);
        return ApiResponse.success(ragApplicationService.embedDocument(projectId, docId, command, traceId), traceId);
    }

    @GetMapping("/documents/{docId}/index-status")
    public ApiResponse<Map<String, Object>> indexStatus(@PathVariable Long projectId,
                                                        @PathVariable Long docId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.getIndexStatus(projectId, docId), traceId);
    }

    @GetMapping("/retrieval-logs")
    public ApiResponse<List<Map<String, Object>>> retrievalLogs(@PathVariable Long projectId,
                                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.listRetrievalLogs(projectId), traceId);
    }
}

