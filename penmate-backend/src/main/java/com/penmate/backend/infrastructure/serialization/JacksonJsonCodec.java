package com.penmate.backend.infrastructure.serialization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.penmate.backend.application.common.serialization.JsonCodec;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;

@Component
public class JacksonJsonCodec implements JsonCodec {

    private static final TypeReference<Map<String, Object>> OBJECT_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalObjectMapper;

    public JacksonJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.canonicalObjectMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Override
    public Object read(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JSON", exception);
        }
    }

    @Override
    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JSON for " + type.getSimpleName(), exception);
        }
    }

    @Override
    public <T> List<T> readList(String json, Class<T> elementType) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JSON list of " + elementType.getSimpleName(), exception);
        }
    }

    @Override
    public Map<String, Object> readObject(String json) {
        try {
            return objectMapper.readValue(json, OBJECT_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JSON object", exception);
        }
    }

    @Override
    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }


    @Override
    public String writeCanonical(Object value) {
        try {
            return canonicalObjectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize canonical JSON", exception);
        }
    }
}
