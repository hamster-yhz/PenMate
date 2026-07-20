package com.penmate.backend.infrastructure.document;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.rag.service.DocumentChunker;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredDocumentContentParserTest {
    private final StructuredDocumentContentParser parser = new StructuredDocumentContentParser();

    @Test
    void parsesHtmlWithoutExecutableContent() {
        var parsed = parser.parse("html", "text/html",
                "<html><body><h1>Title</h1><script>alert(1)</script><p>Body text.</p></body></html>"
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.normalizedText()).contains("Title", "Body text").doesNotContain("alert(1)");
        assertThat(parsed.detectedMimeType()).isEqualTo("text/html");
    }

    @Test
    void rejectsInvalidUtf8AndMimeMismatch() {
        assertThatThrownBy(() -> parser.parse("txt", "text/plain", new byte[]{(byte) 0xc3, 0x28}))
                .isInstanceOf(BusinessException.class).hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> parser.parse("html", "text/plain", "text".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("MIME");
    }

    @Test
    void chunksOnTextBoundariesWithBoundedOverlap() {
        String text = "First paragraph has several words.\n\nSecond paragraph also has several words.\n\nThird paragraph ends here.";
        var chunks = new DocumentChunker(20).chunk(text, 45, 8, 60);

        assertThat(chunks).hasSizeGreaterThan(1).allMatch(chunk -> chunk.length() <= 60);
        assertThat(chunks.getFirst()).contains("First paragraph");
        assertThat(chunks.getLast()).contains("Third paragraph");
    }
}
