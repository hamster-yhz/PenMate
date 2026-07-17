package com.penmate.backend.application.storybible;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.penmate.backend.application.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Component
public class StoryBiblePatchValidator {

    private static final Set<String> OPERATIONS = Set.of("add", "replace", "remove");
    private static final Set<String> BASE_FIELDS = Set.of("title", "summary", "bodyMarkdown");

    private final ObjectMapper objectMapper;
    private final StoryBibleSchemaValidator schemaValidator;

    public StoryBiblePatchValidator(ObjectMapper objectMapper, StoryBibleSchemaValidator schemaValidator) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator");
    }

    public ValidatedPatch validate(String patchJson, String fieldSchemaJson) {
        JsonNode patchDocument = parsePatch(patchJson);
        JsonNode fieldSchema = schemaValidator.parseSchema(fieldSchemaJson);
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
            return new ValidatedPatch(JsonPatch.fromJson(patchDocument), List.copyOf(paths));
        } catch (IOException ex) {
            throw BusinessException.badRequest("patchJson is not a valid RFC 6902 patch");
        }
    }

    public JsonNode apply(JsonNode state, ValidatedPatch patch) {
        try {
            return patch.patch().apply(state.deepCopy());
        } catch (JsonPatchException ex) {
            throw BusinessException.badRequest("patchJson cannot be applied to the node state: " + ex.getMessage());
        }
    }

    private JsonNode parsePatch(String patchJson) {
        try {
            JsonNode patch = objectMapper.readTree(patchJson == null ? "" : patchJson);
            if (patch == null || !patch.isArray() || patch.isEmpty()) {
                throw BusinessException.badRequest("patchJson must be a non-empty RFC 6902 array");
            }
            return patch;
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest("patchJson must be valid JSON");
        }
    }

    private void validatePath(String path, JsonNode fieldSchema) {
        JsonPointer pointer;
        try {
            pointer = JsonPointer.compile(path);
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("patchJson contains an invalid JSON Pointer: " + path);
        }
        if (pointer.matches()) {
            throw BusinessException.badRequest("patchJson cannot replace the complete node state");
        }
        String root = pointer.getMatchingProperty();
        JsonPointer tail = pointer.tail();
        if (BASE_FIELDS.contains(root) && tail.matches()) {
            return;
        }
        if (!"attributes".equals(root) || tail.matches() || !schemaAllows(fieldSchema, tail)) {
            throw BusinessException.badRequest("patchJson path is not allowed by the node type schema: " + path);
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
                if (current.isMissingNode() || current.isBoolean() && !current.booleanValue()) {
                    return false;
                }
            } else {
                JsonNode additional = current.get("additionalProperties");
                if (additional == null || additional.isBoolean() && additional.booleanValue()) {
                    return true;
                }
                if (additional.isObject()) {
                    current = additional;
                } else {
                    return false;
                }
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

    public record ValidatedPatch(JsonPatch patch, List<String> paths) {
    }
}
