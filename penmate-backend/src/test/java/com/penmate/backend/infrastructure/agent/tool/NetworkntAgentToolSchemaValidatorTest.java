package com.penmate.backend.infrastructure.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkntAgentToolSchemaValidatorTest {

    @Test
    void validates_arguments_against_the_registered_schema() {
        NetworkntAgentToolSchemaValidator validator = new NetworkntAgentToolSchemaValidator(new ObjectMapper());
        validator.register("search", """
                {
                  "type":"object",
                  "properties":{"query":{"type":"string","minLength":1}},
                  "required":["query"],
                  "additionalProperties":false
                }
                """);

        validator.validate("search", "{\"query\":\"Mira\"}");
        assertThatThrownBy(() -> validator.validate("search", "{\"query\":\"\",\"extra\":true}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tool arguments do not match schema");
    }

    @Test
    void rejects_invalid_schema_during_registration() {
        NetworkntAgentToolSchemaValidator validator = new NetworkntAgentToolSchemaValidator(new ObjectMapper());

        assertThatThrownBy(() -> validator.register("broken", "[]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tool parameters schema must be a JSON object: broken");
    }
}
