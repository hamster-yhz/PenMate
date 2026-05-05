package com.penmate.backend.application.agent.tool.definition;

/**
 * Tool 面向人类的展示信息。
 * <p>该对象只承载稳定的人类可读名称，不再混入 LLM 暴露描述。</p>
 */
public record ToolPresentation(
        String displayName
) {
}
