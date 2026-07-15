package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.preflight.AgentBehaviorType;

import java.util.List;
import java.util.Objects;

public record AgentRouteDecision(
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
    public AgentRouteDecision {
        behaviorType = Objects.requireNonNull(behaviorType, "behaviorType");
        executionPromptProfile = required(executionPromptProfile, "executionPromptProfile");
        reasoningSummary = required(reasoningSummary, "reasoningSummary");
        decisionTraceJson = required(decisionTraceJson, "decisionTraceJson");
        intentTags = List.copyOf(intentTags == null ? List.of() : intentTags);
        hardConstraints = List.copyOf(hardConstraints == null ? List.of() : hardConstraints);
        enabledSkills = List.copyOf(enabledSkills == null ? List.of() : enabledSkills);
        enabledTools = List.copyOf(enabledTools == null ? List.of() : enabledTools);
        outputExpectation = outputExpectation == null ? null : outputExpectation.trim();
    }

    public AgentRouteDecision(
            AgentBehaviorType behaviorType,
            String executionPromptProfile,
            boolean includeStyleContext,
            boolean includeRagContext,
            boolean includeStoryBibleContext,
            String reasoningSummary,
            String decisionTraceJson
    ) {
        this(behaviorType, executionPromptProfile, includeStyleContext, includeRagContext, includeStoryBibleContext,
                reasoningSummary, decisionTraceJson, List.of(), List.of(), List.of(), List.of(), null,
                false, false, false);
    }

    public static AgentRouteDecision fromTaskType(String taskType, boolean hasStyleContext) {
        String normalized = taskType == null ? "default" : taskType.trim().toLowerCase(java.util.Locale.ROOT);
        String profile = normalized.contains("rewrite") ? "rewrite"
                : normalized.contains("world") ? "world-build" : "default";
        AgentBehaviorType behavior = normalized.contains("rewrite")
                ? AgentBehaviorType.REWRITE : AgentBehaviorType.WRITE;
        return new AgentRouteDecision(
                behavior,
                profile,
                hasStyleContext,
                false,
                true,
                "Explicit task type route",
                "{\"source\":\"task_type\"}",
                List.of(normalized),
                List.of(),
                List.of(),
                List.of(),
                null,
                false,
                false,
                false
        );
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
