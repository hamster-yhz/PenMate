package com.penmate.backend.application.agent.prompt;

import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;

import java.util.List;

/**
 * Stable prompt assembly result.
 * <p>
 * Snapshot policy: all fields are snapshot-safe. In particular, {@code assembledPromptPreview} is persisted as a
 * recovery/debug preview and should not be renamed to another semantic alias.
 */
public record PromptPlan(
        List<PromptModulePlan> modules,
        List<AgentLlmToolSchema> toolSchemas,
        String finalProfile,
        String stablePrefix,
        String dynamicContext,
        String assembledPromptPreview
) {

    public PromptPlan {
        modules = List.copyOf(modules == null ? List.of() : modules);
        toolSchemas = List.copyOf(toolSchemas == null ? List.of() : toolSchemas);
        finalProfile = normalize(finalProfile);
        stablePrefix = normalize(stablePrefix);
        dynamicContext = normalize(dynamicContext);
        assembledPromptPreview = normalize(assembledPromptPreview);
    }

    public PromptPlan(List<PromptModulePlan> modules, String finalProfile,
                      String assembledPromptPreview) {
        this(modules, List.of(), finalProfile, assembledPromptPreview, "", assembledPromptPreview);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
