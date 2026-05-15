package com.penmate.backend.application.agent.prompt;

/**
 * Stable prompt module contract used by prompt composition.
 * <p>
 * Snapshot policy: all fields are included in {@link PromptPlan} snapshot JSON so downstream recovery can
 * reconstruct which prompt modules were assembled.
 */
public record PromptModulePlan(
        String moduleKey,
        String source,
        boolean enabled,
        String reasoning
) {

    public PromptModulePlan {
        moduleKey = normalize(moduleKey);
        source = normalize(source);
        reasoning = normalize(reasoning);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
