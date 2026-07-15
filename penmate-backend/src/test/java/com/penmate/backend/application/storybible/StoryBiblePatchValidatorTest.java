package com.penmate.backend.application.storybible;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryBiblePatchValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StoryBiblePatchValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StoryBiblePatchValidator(objectMapper, new StoryBibleSchemaValidator(objectMapper));
    }

    @Test
    void should_validate_and_apply_supported_patch() throws Exception {
        String schema = """
                {"type":"object","properties":{"status":{"type":"string"}},"additionalProperties":false}
                """;
        StoryBiblePatchValidator.ValidatedPatch patch = validator.validate("""
                [{"op":"replace","path":"/attributes/status","value":"wounded"}]
                """, schema);

        JsonNode result = validator.apply(objectMapper.readTree("""
                {"title":"Mira","summary":null,"bodyMarkdown":null,"attributes":{"status":"healthy"}}
                """), patch);

        assertThat(patch.paths()).containsExactly("/attributes/status");
        assertThat(result.at("/attributes/status").asText()).isEqualTo("wounded");
    }

    @Test
    void should_reject_unsupported_operations_and_paths_outside_schema() {
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
