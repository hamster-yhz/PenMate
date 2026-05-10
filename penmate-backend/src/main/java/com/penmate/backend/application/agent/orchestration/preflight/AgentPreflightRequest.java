package com.penmate.backend.application.agent.orchestration.preflight;

public record AgentPreflightRequest(
        Long projectId,
        Long conversationId,
        Long chapterId,
        String userMessage
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
        userMessage = userMessage.trim();
    }
}
