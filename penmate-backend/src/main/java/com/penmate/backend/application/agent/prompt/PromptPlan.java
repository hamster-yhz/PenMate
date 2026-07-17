package com.penmate.backend.application.agent.prompt;

import java.util.List;

/**
 * Stable prompt assembly result.
 * <p>
 * Snapshot policy: all fields are snapshot-safe. In particular, {@code assembledPromptPreview} is persisted as a
 * recovery/debug preview and should not be renamed to another semantic alias.
 */
public record PromptPlan(
        List<PromptModulePlan> modules,
        List<String> skills,
        String finalProfile,
        String stablePrefix,
        String dynamicContext,
        String assembledPromptPreview
) {

    public PromptPlan {
        modules = List.copyOf(modules == null ? List.of() : modules);
        skills = List.copyOf(skills == null ? List.of() : skills);
        finalProfile = normalize(finalProfile);
        stablePrefix = normalize(stablePrefix);
        dynamicContext = normalize(dynamicContext);
        assembledPromptPreview = normalize(assembledPromptPreview);
    }

    public PromptPlan(List<PromptModulePlan> modules, List<String> skills, String finalProfile,
                      String assembledPromptPreview) {
        this(modules, skills, finalProfile, assembledPromptPreview, "", assembledPromptPreview);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
