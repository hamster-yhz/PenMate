package com.penmate.backend.infrastructure.storybible;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.storybible.StoryBiblePatchValidator;
import com.penmate.backend.application.storybible.StoryBibleSchemaValidator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JacksonStoryBibleValidationEngine implements StoryBibleSchemaValidator, StoryBiblePatchValidator {

    private static final TypeReference<Map<String, Object>> OBJECT_TYPE = new TypeReference<>() { };
    private static final Set<String> OPERATIONS = Set.of("add", "replace", "remove");
    private static final Set<String> BASE_FIELDS = Set.of("title", "summary", "bodyMarkdown");

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;

    public JacksonStoryBibleValidationEngine(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    }

    @Override
    public void parseSchema(String schemaJson) {
        schema(schemaJson);
    }

    @Override
    public void validateAttributes(String attributesJson, String schemaJson) {
        JsonNode attributes = parseObject(attributesJson, "attributesJson");
        Set<ValidationMessage> errors = schema(schemaJson).validate(attributes);
        if (!errors.isEmpty()) {
            String detail = errors.stream().map(ValidationMessage::getMessage).sorted()
                    .collect(Collectors.joining("; "));
            throw BusinessException.badRequest(
                    "attributesJson does not match the node type schema: " + detail);
        }
    }

    @Override
    public ValidatedPatch validate(String patchJson, String fieldSchemaJson) {
        JsonNode patchDocument = parsePatch(patchJson);
        JsonNode fieldSchema = parseSchemaNode(fieldSchemaJson);
        Set<String> paths = new LinkedHashSet<>();
        for (JsonNode operation : patchDocument) {
            if (!operation.isObject()) {
                throw BusinessException.badRequest("patchJson operations must be JSON objects");
            }
            String op = requiredText(operation, "op").toLowerCase(Locale.ROOT);
            if (!OPERATIONS.contains(op)) {
                throw BusinessException.badRequest("patchJson only supports add, replace, and remove");
            }
            String path = requiredText(operation, "path");
            validatePath(path, fieldSchema);
            if (!"remove".equals(op) && !operation.has("value")) {
                throw BusinessException.badRequest("patchJson " + op + " operations require value");
            }
            paths.add(path);
        }
        try {
            JsonPatch.fromJson(patchDocument);
            return new ValidatedPatch(patchDocument.toString(), List.copyOf(paths));
        } catch (IOException exception) {
            throw BusinessException.badRequest("patchJson is not a valid RFC 6902 patch");
        }
    }

    @Override
    public Map<String, Object> apply(Map<String, Object> state, ValidatedPatch patch) {
        try {
            JsonNode patchDocument = objectMapper.readTree(patch.patchJson());
            JsonNode updated = JsonPatch.fromJson(patchDocument).apply(objectMapper.valueToTree(state));
            return objectMapper.convertValue(updated, OBJECT_TYPE);
        } catch (IOException | JsonPatchException exception) {
            throw BusinessException.badRequest(
                    "patchJson cannot be applied to the node state: " + exception.getMessage());
        }
    }

    private JsonSchema schema(String schemaJson) {
        try {
            return schemaFactory.getSchema(parseSchemaNode(schemaJson));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw BusinessException.badRequest("fieldSchemaJson must be a valid JSON Schema");
        }
    }

    private JsonNode parseSchemaNode(String schemaJson) {
        JsonNode schema = parseObject(schemaJson, "fieldSchemaJson");
        try {
            schemaFactory.getSchema(schema);
        } catch (RuntimeException exception) {
            throw BusinessException.badRequest("fieldSchemaJson must be a valid JSON Schema");
        }
        return schema;
    }

    private JsonNode parseObject(String value, String field) {
        try {
            JsonNode node = objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
            if (node == null || !node.isObject()) {
                throw BusinessException.badRequest(field + " must be a JSON object");
            }
            return node;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw BusinessException.badRequest(field + " must be valid JSON");
        }
    }

    private JsonNode parsePatch(String patchJson) {
        try {
            JsonNode patch = objectMapper.readTree(patchJson == null ? "" : patchJson);
            if (patch == null || !patch.isArray() || patch.isEmpty()) {
                throw BusinessException.badRequest("patchJson must be a non-empty RFC 6902 array");
            }
            return patch;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw BusinessException.badRequest("patchJson must be valid JSON");
        }
    }

    private void validatePath(String path, JsonNode fieldSchema) {
        JsonPointer pointer;
        try {
            pointer = JsonPointer.compile(path);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("patchJson contains an invalid JSON Pointer: " + path);
        }
        if (pointer.matches()) {
            throw BusinessException.badRequest("patchJson cannot replace the complete node state");
        }
        String root = pointer.getMatchingProperty();
        JsonPointer tail = pointer.tail();
        if (BASE_FIELDS.contains(root) && tail.matches()) return;
        if (!"attributes".equals(root) || tail.matches() || !schemaAllows(fieldSchema, tail)) {
            throw BusinessException.badRequest(
                    "patchJson path is not allowed by the node type schema: " + path);
        }
    }

    private boolean schemaAllows(JsonNode schema, JsonPointer pointer) {
        JsonNode current = schema;
        JsonPointer remaining = pointer;
        while (!remaining.matches()) {
            String token = remaining.getMatchingProperty();
            JsonNode properties = current.path("properties");
            if (properties.isObject() && properties.has(token)) {
                current = properties.get(token);
            } else if (isArraySchema(current) && isArrayToken(token)) {
                current = current.path("items");
                if (current.isMissingNode() || current.isBoolean() && !current.booleanValue()) return false;
            } else {
                JsonNode additional = current.get("additionalProperties");
                if (additional == null || additional.isBoolean() && additional.booleanValue()) return true;
                if (additional.isObject()) current = additional; else return false;
            }
            remaining = remaining.tail();
        }
        return true;
    }

    private boolean isArraySchema(JsonNode schema) {
        JsonNode type = schema.get("type");
        return type != null && (type.isTextual() && "array".equals(type.textValue())
                || type.isArray() && containsText(type, "array"));
    }

    private boolean containsText(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (value.isTextual() && expected.equals(value.textValue())) return true;
        }
        return false;
    }

    private boolean isArrayToken(String token) {
        if ("-".equals(token)) return true;
        if (token.isEmpty()) return false;
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) return false;
        }
        return true;
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw BusinessException.badRequest("patchJson operation " + field + " is required");
        }
        return value.textValue();
    }
}
