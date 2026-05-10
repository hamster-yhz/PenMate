package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;

import java.util.Objects;

public record AgentContextRoutingRequest(
        Long projectId,
        Long conversationId,
        Long chapterId,
        String userMessage,
        String styleSnapshot,
        AgentPreflightDecision decision
) {

    public AgentContextRoutingRequest {
        Objects.requireNonNull(decision, "decision");
    }
}
