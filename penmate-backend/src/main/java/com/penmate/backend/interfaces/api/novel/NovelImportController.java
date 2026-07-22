package com.penmate.backend.interfaces.api.novel;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.novel.NovelCoverApplicationService;
import com.penmate.backend.application.novel.NovelTxtImportApplicationService;
import com.penmate.backend.application.novel.NovelTxtImportApplicationService.ImportChapterPreview;
import com.penmate.backend.application.novel.NovelTxtImportApplicationService.ImportPreview;
import com.penmate.backend.application.novel.NovelTxtImportApplicationService.ImportVolumePreview;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.novel.dto.NovelTxtImportDto;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/novels/imports/txt")
public class NovelImportController {

    private final NovelTxtImportApplicationService importApplicationService;
    private final NovelCoverApplicationService coverApplicationService;

    public NovelImportController(NovelTxtImportApplicationService importApplicationService,
                                 NovelCoverApplicationService coverApplicationService) {
        this.importApplicationService = importApplicationService;
        this.coverApplicationService = coverApplicationService;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportPreview> preview(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")) {
            throw BusinessException.badRequest("Only .txt files are supported");
        }
        try {
            return ApiResponse.success(importApplicationService.preview(filename, file.getBytes()), traceId);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read uploaded TXT file", exception);
        }
    }

    @PostMapping
    public ApiResponse<NovelProject> importProject(
            @Valid @RequestBody NovelTxtImportDto dto,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        ImportPreview preview = new ImportPreview(dto.getProjectTitle(), dto.getVolumes().stream()
                .map(volume -> new ImportVolumePreview(volume.getTitle(), volume.getChapters().stream()
                        .map(chapter -> new ImportChapterPreview(chapter.getTitle(), chapter.getContent()))
                        .toList()))
                .toList());
        NovelProject project = importApplicationService.importProject(id(authentication), preview, traceId);
        return ApiResponse.success(coverApplicationService.decorate(project), traceId);
    }
}
