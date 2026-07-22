package com.penmate.backend.interfaces.api.novel;

import com.penmate.backend.application.novel.export.NovelExportService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static com.penmate.backend.interfaces.api.common.AuthenticatedActor.id;

@RestController
@RequestMapping("/api/v1/novels")
public class NovelExportController {

    private final NovelExportService novelExportService;

    public NovelExportController(NovelExportService novelExportService) {
        this.novelExportService = novelExportService;
    }

    @GetMapping("/{projectId}/exports/{format}")
    public ResponseEntity<byte[]> export(@PathVariable String projectId,
                                         @PathVariable String format,
                                         Authentication authentication) {
        NovelExportService.ExportedNovel exported = novelExportService.export(
                parseId(projectId), id(authentication), format);
        byte[] content = exported.content();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(exported.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(exported.fileName(), StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(content.length);
        headers.setCacheControl(CacheControl.noStore());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    private Long parseId(String value) {
        if (value == null || !value.matches("\\d+")) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest(
                    "projectId must be numeric");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest(
                    "projectId is out of range");
        }
    }
}
