package com.penmate.backend.application.storybible;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.storybible.model.StoryBibleSemanticFamily;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StoryBibleSystemTypeCatalog {
    private static final String RESOURCE = "story-bible/system-node-types.json";

    private final List<Definition> definitions;

    public StoryBibleSystemTypeCatalog(JsonCodec jsonCodec) {
        this.definitions = load(jsonCodec);
    }

    public List<Definition> definitions() {
        return definitions;
    }

    public Definition require(String typeCode) {
        return definitions.stream().filter(item -> item.typeCode().equals(typeCode)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown system Story Bible type: " + typeCode));
    }

    private List<Definition> load(JsonCodec jsonCodec) {
        Map<String, Object> root = jsonCodec.readObject(readResource());
        Object rawTypes = root.get("types");
        if (!(rawTypes instanceof List<?> types) || types.isEmpty()) {
            throw new IllegalStateException("Story Bible system type catalog must contain types");
        }
        List<Definition> result = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        for (Object rawType : types) {
            Map<String, Object> type = object(rawType, "type");
            String code = text(type, "code");
            if (!codes.add(code)) throw new IllegalStateException("Duplicate Story Bible system type: " + code);
            StoryBibleSemanticFamily family = StoryBibleSemanticFamily.valueOf(text(type, "family"));
            String displayName = text(type, "displayName");
            String iconCode = text(type, "iconCode");
            int sortOrder = number(type, "sortOrder").intValue();
            result.add(new Definition(code, family, displayName, iconCode, sortOrder,
                    jsonCodec.writeCanonical(schema(type))));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> schema(Map<String, Object> type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("title", text(type, "displayName"));
        schema.put("description", text(type, "description"));
        schema.put("x-penmate-description", text(type, "description"));
        schema.put("x-penmate-color", text(type, "color"));
        schema.put("x-penmate-title-placeholder", text(type, "titlePlaceholder"));
        schema.put("x-penmate-summary-placeholder", text(type, "summaryPlaceholder"));
        schema.put("x-penmate-sections", list(type, "sections"));

        Map<String, Object> properties = new LinkedHashMap<>();
        for (Object rawField : list(type, "fields")) {
            Map<String, Object> field = object(rawField, "field");
            String key = text(field, "key");
            Map<String, Object> property = new LinkedHashMap<>();
            String fieldType = text(field, "type");
            property.put("type", jsonType(fieldType));
            property.put("title", text(field, "label"));
            property.put("x-penmate-control", fieldType);
            property.put("x-penmate-section", text(field, "section"));
            property.put("x-penmate-order", number(field, "order").intValue());
            copy(field, property, "placeholder", "x-penmate-placeholder");
            copy(field, property, "help", "description");
            copy(field, property, "minimum", "minimum");
            copy(field, property, "maximum", "maximum");
            if ("string-list".equals(fieldType)) property.put("items", Map.of("type", "string"));
            if ("enum".equals(fieldType)) applyOptions(field, property);
            properties.put(key, property);
        }
        schema.put("properties", properties);
        // Existing projects could already contain API-written custom attributes on a system type.
        schema.put("additionalProperties", true);
        return schema;
    }

    private void applyOptions(Map<String, Object> field, Map<String, Object> property) {
        List<String> values = new ArrayList<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (Object rawOption : list(field, "options")) {
            Map<String, Object> option = object(rawOption, "option");
            String value = text(option, "value");
            values.add(value);
            labels.put(value, text(option, "label"));
        }
        property.put("enum", values);
        property.put("x-penmate-enum-labels", labels);
    }

    private String jsonType(String fieldType) {
        return switch (fieldType) {
            case "integer" -> "integer";
            case "number" -> "number";
            case "boolean" -> "boolean";
            case "string-list" -> "array";
            default -> "string";
        };
    }

    private void copy(Map<String, Object> source, Map<String, Object> target, String sourceKey, String targetKey) {
        if (source.containsKey(sourceKey) && source.get(sourceKey) != null) target.put(targetKey, source.get(sourceKey));
    }

    private String readResource() {
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load Story Bible system type catalog", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalStateException(name + " must be an object");
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(Map<String, Object> value, String key) {
        Object result = value.get(key);
        if (!(result instanceof List<?> raw)) throw new IllegalStateException(key + " must be an array");
        return (List<Object>) raw;
    }

    private String text(Map<String, Object> value, String key) {
        Object result = value.get(key);
        if (!(result instanceof String text) || text.isBlank()) {
            throw new IllegalStateException(key + " must be a non-empty string");
        }
        return text.trim();
    }

    private Number number(Map<String, Object> value, String key) {
        Object result = value.get(key);
        if (!(result instanceof Number number)) throw new IllegalStateException(key + " must be a number");
        return number;
    }

    public record Definition(String typeCode, StoryBibleSemanticFamily semanticFamily, String displayName,
                             String iconCode, int sortOrder, String fieldSchemaJson) {
    }
}
