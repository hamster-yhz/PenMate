package com.penmate.backend.domain.agent.model;

import java.util.Objects;

public record AgentLlmToolCallPayload(
        String id,
        String type,
        String functionName,
        String argumentsJson
) {

    public AgentLlmToolCallPayload {
        id = requireText(id, "id");
        type = normalizeType(type);
        functionName = requireText(functionName, "functionName");
        argumentsJson = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson.trim();
    }

    private static String normalizeType(String value) {
        return value == null || value.isBlank() ? "function" : value.trim();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
