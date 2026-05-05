package com.penmate.backend.application.agent.tool.definition;

/**
 * Tool 对 LLM 的暴露定义。
 * <p>当 {@code exposedToLlm} 为 {@code true} 时，LLM 所需的描述文案与参数 schema 都应从这里读取，
 * 避免与人类展示信息形成双真源。</p>
 */
public record ToolExposure(
        boolean exposedToLlm,
        String llmDescription,
        String parametersJsonSchema
) {
}
