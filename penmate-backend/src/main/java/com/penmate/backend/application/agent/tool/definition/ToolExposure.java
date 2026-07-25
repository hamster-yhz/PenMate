package com.penmate.backend.application.agent.tool.definition;

import java.util.Locale;
import java.util.Set;

/**
 * Tool 对 LLM 的暴露定义。
 * <p>LLM 所需的描述文案与参数 schema 都从这里读取，避免与人类展示信息形成双真源。</p>
 */
public record ToolExposure(
        ToolLifecycleStatus lifecycleStatus,
        String llmDescription,
        String parametersJsonSchema,
        Set<String> executionProfiles
) {

    public ToolExposure(ToolLifecycleStatus lifecycleStatus, String llmDescription, String parametersJsonSchema) {
        this(lifecycleStatus, llmDescription, parametersJsonSchema, Set.of("*"));
    }

    public ToolExposure {
        lifecycleStatus = java.util.Objects.requireNonNull(lifecycleStatus, "lifecycleStatus");
        executionProfiles = Set.copyOf(executionProfiles == null || executionProfiles.isEmpty()
                ? Set.of("*")
                : executionProfiles.stream().map(ToolExposure::normalizeProfile).collect(java.util.stream.Collectors.toSet()));
    }

    public boolean supportsProfile(String executionProfile) {
        return executionProfiles.contains("*") || executionProfiles.contains(normalizeProfile(executionProfile));
    }

    private static String normalizeProfile(String value) {
        return value == null || value.isBlank() ? "default" : value.trim().toLowerCase(Locale.ROOT);
    }
}
