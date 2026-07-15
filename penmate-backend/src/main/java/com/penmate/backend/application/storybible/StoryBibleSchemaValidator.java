package com.penmate.backend.application.storybible;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.penmate.backend.application.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class StoryBibleSchemaValidator {

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;

    public StoryBibleSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    }

    public JsonNode parseSchema(String schemaJson) {
        JsonNode schema = parseObject(schemaJson, "fieldSchemaJson");
        try {
            schemaFactory.getSchema(schema);
        } catch (RuntimeException ex) {
            throw BusinessException.badRequest("fieldSchemaJson must be a valid JSON Schema");
        }
        return schema;
    }

    public JsonNode validateAttributes(String attributesJson, String schemaJson) {
        JsonNode attributes = parseObject(attributesJson, "attributesJson");
        JsonSchema schema;
        try {
            schema = schemaFactory.getSchema(parseSchema(schemaJson));
        } catch (RuntimeException ex) {
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw BusinessException.badRequest("fieldSchemaJson must be a valid JSON Schema");
        }
        Set<ValidationMessage> errors = schema.validate(attributes);
        if (!errors.isEmpty()) {
            String detail = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw BusinessException.badRequest("attributesJson does not match the node type schema: " + detail);
        }
        return attributes;
    }

    private JsonNode parseObject(String value, String field) {
        try {
            JsonNode node = objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
            if (node == null || !node.isObject()) {
                throw BusinessException.badRequest(field + " must be a JSON object");
            }
            return node;
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest(field + " must be valid JSON");
        }
    }
}
