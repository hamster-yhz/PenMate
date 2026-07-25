package com.penmate.backend.application.storybible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import com.penmate.backend.infrastructure.storybible.JacksonStoryBibleValidationEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoryBibleSystemTypeCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonCodec jsonCodec = new JacksonJsonCodec(objectMapper);

    @Test
    void should_load_complete_minimum_novel_ontology_with_valid_schemas() {
        StoryBibleSystemTypeCatalog catalog = new StoryBibleSystemTypeCatalog(jsonCodec);
        JacksonStoryBibleValidationEngine validation = new JacksonStoryBibleValidationEngine(objectMapper);

        assertThat(catalog.definitions()).hasSize(19);
        assertThat(catalog.definitions()).extracting(StoryBibleSystemTypeCatalog.Definition::typeCode)
                .contains("STORY_CORE", "CHARACTER", "RELATIONSHIP_ARC", "CULTURE", "PLOTLINE", "EVENT", "FACT");
        catalog.definitions().forEach(definition -> {
            validation.parseSchema(definition.fieldSchemaJson());
            assertThat(jsonCodec.readObject(definition.fieldSchemaJson()))
                    .containsKeys("properties", "x-penmate-sections", "x-penmate-description");
        });
    }

    @Test
    void every_system_type_should_have_type_specific_fields() {
        StoryBibleSystemTypeCatalog catalog = new StoryBibleSystemTypeCatalog(jsonCodec);

        catalog.definitions().forEach(definition -> {
            var schema = jsonCodec.readObject(definition.fieldSchemaJson());
            assertThat((java.util.Map<?, ?>) schema.get("properties"))
                    .as(definition.typeCode())
                    .isNotEmpty();
        });
    }
}
