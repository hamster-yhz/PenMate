package com.penmate.backend.application.agent.orchestration.profile;

import java.util.List;

/**
 * Stable, immutable task profiler contract consumed by workflow, prompt composition and context building.
 * <p>
 * Snapshot policy: all fields in this record enter snapshot and should be persisted for resume/recovery.
 * There are no runtime-only fields here.
 * Do not introduce alias fields such as taskType / behaviorType / profileType for the same semantics.
 */
public record TaskProfile(
        List<TaskIntentTag> intentTags,
        String executionProfile,
        List<String> skills,
        List<String> tools,
        List<String> hardConstraints,
        String outputExpectation,
        boolean needsApproval,
        boolean includeStoryBible,
        boolean includeRag,
        String reasoningSummary
) {

    public TaskProfile {
        intentTags = List.copyOf(intentTags == null ? List.of() : intentTags);
        executionProfile = normalize(executionProfile);
        skills = List.copyOf(skills == null ? List.of() : skills);
        tools = List.copyOf(tools == null ? List.of() : tools);
        hardConstraints = List.copyOf(hardConstraints == null ? List.of() : hardConstraints);
        outputExpectation = normalize(outputExpectation);
        reasoningSummary = normalize(reasoningSummary);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
