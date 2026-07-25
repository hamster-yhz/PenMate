package com.penmate.backend.interfaces.api.novel;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.novel.importing.NovelImportApplicationService;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.novel.dto.NovelImportDto;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static com.penmate.backend.interfaces.api.common.AuthenticatedActor.id;

@RestController
@RequestMapping("/api/v1/novels/imports")
public class NovelImportController {
    private final NovelImportApplicationService imports;

    public NovelImportController(NovelImportApplicationService imports) {
        this.imports = imports;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<NovelImportApplicationService.PreviewResult> preview(
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        if (file.isEmpty()) throw BusinessException.badRequest("Import file is empty");
        try {
            return ApiResponse.success(imports.preview(id(authentication), file.getOriginalFilename(), file.getInputStream()), traceId);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read import file", exception);
        }
    }

    @PostMapping("/{sessionId}/confirm")
    public ApiResponse<NovelImportApplicationService.SessionView> confirm(
            @PathVariable String sessionId,
            @Valid @RequestBody NovelImportDto dto,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(imports.confirm(id(authentication), numeric(sessionId), dto.toDraft()), traceId);
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<NovelImportApplicationService.SessionView> status(
            @PathVariable String sessionId, Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(imports.get(id(authentication), numeric(sessionId)), traceId);
    }

    @PostMapping("/{sessionId}/pause")
    public ApiResponse<NovelImportApplicationService.SessionView> pause(
            @PathVariable String sessionId, Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(imports.pause(id(authentication), numeric(sessionId)), traceId);
    }

    @PostMapping("/{sessionId}/resume")
    public ApiResponse<NovelImportApplicationService.SessionView> resume(
            @PathVariable String sessionId, Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(imports.resume(id(authentication), numeric(sessionId)), traceId);
    }

    @PostMapping("/{sessionId}/cancel")
    public ApiResponse<NovelImportApplicationService.SessionView> cancel(
            @PathVariable String sessionId, Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(imports.cancel(id(authentication), numeric(sessionId)), traceId);
    }

    @PostMapping("/{sessionId}/retry")
    public ApiResponse<NovelImportApplicationService.SessionView> retry(
            @PathVariable String sessionId, Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(imports.retry(id(authentication), numeric(sessionId)), traceId);
    }

    private Long numeric(String value) {
        if (value == null || !value.matches("\\d+")) throw BusinessException.badRequest("sessionId must be numeric");
        try { return Long.valueOf(value); }
        catch (NumberFormatException exception) { throw BusinessException.badRequest("sessionId is out of range"); }
    }
}
