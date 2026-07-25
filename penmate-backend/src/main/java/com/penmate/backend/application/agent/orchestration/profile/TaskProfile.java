package com.penmate.backend.application.agent.orchestration.profile;

import java.util.List;
import java.util.Locale;

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
        tools = List.copyOf(tools == null ? List.of() : tools);
        hardConstraints = List.copyOf(hardConstraints == null ? List.of() : hardConstraints);
        outputExpectation = normalize(outputExpectation);
        reasoningSummary = normalize(reasoningSummary);
    }

    public static TaskProfile fromTaskType(String taskType) {
        String normalized = taskType == null ? "" : taskType.trim().toLowerCase(Locale.ROOT);
        String profile = normalized.contains("rewrite") ? "rewrite"
                : normalized.contains("world") ? "world-build" : "default";
        return new TaskProfile(
                List.of(), profile, List.of(), List.of(), null,
                false, true, false, "Explicit task type route"
        );
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

}
