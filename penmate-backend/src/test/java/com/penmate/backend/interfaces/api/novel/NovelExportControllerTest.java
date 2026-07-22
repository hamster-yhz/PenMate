package com.penmate.backend.interfaces.api.novel;

import com.penmate.backend.application.novel.export.NovelExportService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NovelExportControllerTest {

    @Test
    void returns_download_headers_and_binary_body() {
        NovelExportService service = mock(NovelExportService.class);
        NovelExportController controller = new NovelExportController(service);
        byte[] content = "novel".getBytes(StandardCharsets.UTF_8);
        when(service.export(2001L, 1001L, "txt")).thenReturn(new NovelExportService.ExportedNovel(
                "长夜.txt", "text/plain;charset=UTF-8", content));

        ResponseEntity<byte[]> response = controller.export(
                "2001", "txt", new UsernamePasswordAuthenticationToken("1001", null, List.of()));

        assertThat(response.getBody()).isEqualTo(content);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("长夜.txt");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(service).export(2001L, 1001L, "txt");
    }
}
