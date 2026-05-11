package com.penmate.backend.application.agent.orchestration.preflight;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;

public record AgentPreflightRequest(
        Long projectId,
        Long conversationId,
        Long chapterId,
        String userMessage,
        AgentLlmExecutionConfig executionConfig
) {

    public AgentPreflightRequest {
        if (projectId == null) {
            throw new NullPointerException("projectId");
        }
        if (conversationId == null) {
            throw new NullPointerException("conversationId");
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
        if (executionConfig == null) {
            throw new NullPointerException("executionConfig");
        }
        userMessage = userMessage.trim();
    }
}
