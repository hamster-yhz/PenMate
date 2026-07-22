package com.penmate.backend.infrastructure.storybible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.storybible.StoryBiblePatchValidator;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonStoryBibleValidationEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StoryBiblePatchValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JacksonStoryBibleValidationEngine(objectMapper);
    }

    @Test
    void validates_and_applies_supported_patch() {
        String schema = """
                {"type":"object","properties":{"status":{"type":"string"}},"additionalProperties":false}
                """;
        StoryBiblePatchValidator.ValidatedPatch patch = validator.validate("""
                [{"op":"replace","path":"/attributes/status","value":"wounded"}]
                """, schema);

        Map<String, Object> result = validator.apply(
                new JacksonJsonCodec(objectMapper).readObject("""
                        {"title":"Mira","summary":null,"bodyMarkdown":null,"attributes":{"status":"healthy"}}
                        """), patch);

        assertThat(patch.paths()).containsExactly("/attributes/status");
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) result.get("attributes");
        assertThat(attributes.get("status")).isEqualTo("wounded");
    }

    @Test
    void rejects_unsupported_operations_and_paths_outside_schema() {
        String schema = """
                {"type":"object","properties":{"status":{"type":"string"}},"additionalProperties":false}
                """;

        assertThatThrownBy(() -> validator.validate(
                "[{\"op\":\"move\",\"from\":\"/title\",\"path\":\"/summary\"}]", schema))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only supports");
        assertThatThrownBy(() -> validator.validate(
                "[{\"op\":\"add\",\"path\":\"/attributes/unknown\",\"value\":1}]", schema))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not allowed");
        assertThatThrownBy(() -> validator.validate(
                "[{\"op\":\"replace\",\"path\":\"/revision\",\"value\":2}]", schema))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not allowed");
    }
}
