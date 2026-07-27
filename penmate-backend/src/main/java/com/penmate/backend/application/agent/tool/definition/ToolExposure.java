package com.penmate.backend.application.agent.tool.definition;

/**
 * Tool 对 LLM 的暴露定义。
 * <p>LLM 所需的描述文案与参数 schema 都从这里读取，避免与人类展示信息形成双真源。</p>
 */
public record ToolExposure(
        ToolLifecycleStatus lifecycleStatus,
        String llmDescription,
        String parametersJsonSchema
) {

    public ToolExposure {
        lifecycleStatus = java.util.Objects.requireNonNull(lifecycleStatus, "lifecycleStatus");
    }
}
