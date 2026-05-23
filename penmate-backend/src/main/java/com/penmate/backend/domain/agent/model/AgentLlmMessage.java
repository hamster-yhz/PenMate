package com.penmate.backend.domain.agent.model;

import java.util.List;
import java.util.Objects;

public record AgentLlmMessage(
        AgentLlmMessageRole role,
        String content,
        List<AgentLlmToolCallPayload> toolCalls,
        String toolCallId
) {

    public AgentLlmMessage {
        role = Objects.requireNonNull(role, "role must not be null");
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolCallId = normalizeNullable(toolCallId);

        if (role == AgentLlmMessageRole.TOOL && toolCallId == null) {
            throw new IllegalArgumentException("toolCallId is required for tool message");
        }
        if (role != AgentLlmMessageRole.ASSISTANT && !toolCalls.isEmpty()) {
            throw new IllegalArgumentException("toolCalls are only allowed for assistant message");
        }
        if (role != AgentLlmMessageRole.TOOL && toolCallId != null) {
            throw new IllegalArgumentException("toolCallId is only allowed for tool message");
        }
    }

    public static AgentLlmMessage system(String content) {
        return new AgentLlmMessage(AgentLlmMessageRole.SYSTEM, content, List.of(), null);
    }

    public static AgentLlmMessage user(String content) {
        return new AgentLlmMessage(AgentLlmMessageRole.USER, content, List.of(), null);
    }

    public static AgentLlmMessage assistant(String content, List<AgentLlmToolCallPayload> toolCalls) {
        return new AgentLlmMessage(AgentLlmMessageRole.ASSISTANT, content, toolCalls, null);
    }

    public static AgentLlmMessage tool(String toolCallId, String content) {
        return new AgentLlmMessage(AgentLlmMessageRole.TOOL, content, List.of(), toolCallId);
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
