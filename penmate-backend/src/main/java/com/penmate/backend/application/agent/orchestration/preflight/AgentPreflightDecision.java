package com.penmate.backend.application.agent.orchestration.preflight;

import java.util.Objects;

public record AgentPreflightDecision(
        AgentBehaviorType behaviorType,
        String executionPromptProfile,
        boolean includeStyleContext,
        boolean includeRagContext,
        boolean includeStoryBibleContext,
        String reasoningSummary,
        String decisionTraceJson
) {

    public AgentPreflightDecision {
        behaviorType = Objects.requireNonNull(behaviorType, "behaviorType");
        if (executionPromptProfile == null || executionPromptProfile.isBlank()) {
            throw new IllegalArgumentException("executionPromptProfile must not be blank");
        }
        if (reasoningSummary == null || reasoningSummary.isBlank()) {
            throw new IllegalArgumentException("reasoningSummary must not be blank");
        }
        if (decisionTraceJson == null || decisionTraceJson.isBlank()) {
            throw new IllegalArgumentException("decisionTraceJson must not be blank");
        }
        executionPromptProfile = executionPromptProfile.trim();
        reasoningSummary = reasoningSummary.trim();
        decisionTraceJson = decisionTraceJson.trim();
    }
}
