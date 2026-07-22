package com.penmate.backend.application.storybible;

public interface StoryBibleSchemaValidator {

    void parseSchema(String schemaJson);

    void validateAttributes(String attributesJson, String schemaJson);
}
