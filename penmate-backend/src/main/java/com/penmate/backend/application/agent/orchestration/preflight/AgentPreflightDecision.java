package com.penmate.backend.application.agent.orchestration.preflight;

import java.util.List;
import java.util.Objects;

public record AgentPreflightDecision(
        AgentBehaviorType behaviorType,
        String executionPromptProfile,
        boolean includeStyleContext,
        boolean includeRagContext,
        boolean includeStoryBibleContext,
        String reasoningSummary,
        String decisionTraceJson,
        List<String> intentTags,
        List<String> hardConstraints,
        List<String> enabledSkills,
        List<String> enabledTools,
        String outputExpectation,
        boolean needsApproval,
        boolean needsStoryBibleUpdate,
        boolean needsClarification
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
        intentTags = List.copyOf(intentTags == null ? List.of() : intentTags);
        hardConstraints = List.copyOf(hardConstraints == null ? List.of() : hardConstraints);
        enabledSkills = List.copyOf(enabledSkills == null ? List.of() : enabledSkills);
        enabledTools = List.copyOf(enabledTools == null ? List.of() : enabledTools);
        outputExpectation = outputExpectation == null ? null : outputExpectation.trim();
    }

    public AgentPreflightDecision(AgentBehaviorType behaviorType,
                                  String executionPromptProfile,
                                  boolean includeStyleContext,
                                  boolean includeRagContext,
                                  boolean includeStoryBibleContext,
                                  String reasoningSummary,
                                  String decisionTraceJson) {
        this(
                behaviorType,
                executionPromptProfile,
                includeStyleContext,
                includeRagContext,
                includeStoryBibleContext,
                reasoningSummary,
                decisionTraceJson,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                false,
                false,
                false
        );
    }
}
