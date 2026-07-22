package com.penmate.backend.application.rag.job;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;

import java.util.Map;

final class RagJobPayload {
    private RagJobPayload() { }

    static Map<String, Object> parse(JsonCodec codec, OpsAsyncJob job) {
        try {
            return codec.readObject(job.getPayloadJson());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid RAG job payload", exception);
        }
    }

    static long requiredLong(Map<String, Object> payload, String field) {
        Long value = longValue(payload.get(field));
        if (value == null) throw new IllegalArgumentException("Missing RAG job field: " + field);
        return value;
    }

    static long longOrDefault(Map<String, Object> payload, String field, long fallback) {
        Long value = longValue(payload.get(field));
        return value == null ? fallback : value;
    }

    static String text(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        return value == null ? "" : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string && string.matches("-?\\d+")) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
