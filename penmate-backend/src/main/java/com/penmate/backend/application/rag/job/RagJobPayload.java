package com.penmate.backend.application.rag.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;

final class RagJobPayload {
    private RagJobPayload() { }

    static JsonNode parse(ObjectMapper mapper, OpsAsyncJob job) {
        try {
            return mapper.readTree(job.getPayloadJson());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid RAG job payload", exception);
        }
    }

    static long requiredLong(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.canConvertToLong()) throw new IllegalArgumentException("Missing RAG job field: " + field);
        return value.longValue();
    }
}
