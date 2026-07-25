package com.penmate.backend.domain.agent.model;

import java.util.List;
import java.util.Objects;

public record AgentLlmMessage(
        AgentLlmMessageRole role,
        String content,
        List<AgentLlmToolCallPayload> toolCalls,
        String toolCallId,
        List<AgentLlmProviderItem> providerItems
) {

    public AgentLlmMessage(AgentLlmMessageRole role,
                           String content,
                           List<AgentLlmToolCallPayload> toolCalls,
                           String toolCallId) {
        this(role, content, toolCalls, toolCallId, List.of());
    }

    public AgentLlmMessage {
        role = Objects.requireNonNull(role, "role must not be null");
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        providerItems = providerItems == null ? List.of() : List.copyOf(providerItems);
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
        return new AgentLlmMessage(AgentLlmMessageRole.SYSTEM, content, List.of(), null, List.of());
    }

    public static AgentLlmMessage user(String content) {
        return new AgentLlmMessage(AgentLlmMessageRole.USER, content, List.of(), null, List.of());
    }

    public static AgentLlmMessage assistant(String content, List<AgentLlmToolCallPayload> toolCalls) {
        return assistant(content, toolCalls, List.of());
    }

    public static AgentLlmMessage assistant(String content,
                                            List<AgentLlmToolCallPayload> toolCalls,
                                            List<AgentLlmProviderItem> providerItems) {
        return new AgentLlmMessage(AgentLlmMessageRole.ASSISTANT, content, toolCalls, null, providerItems);
    }

    public static AgentLlmMessage tool(String toolCallId, String content) {
        return new AgentLlmMessage(AgentLlmMessageRole.TOOL, content, List.of(), toolCallId, List.of());
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
