package com.penmate.backend.application.agent.tool.definition;

/**
 * 单个 Agent tool 的完整声明快照。
 * <p>该 record 聚合一个 tool 在应用层所需的三类信息：</p>
 * <ol>
 *   <li>稳定标识 {@code toolCode}；</li>
 *   <li>面向人类界面的展示信息 {@link ToolPresentation}；</li>
 *   <li>面向 LLM 的暴露定义与治理策略。</li>
 * </ol>
 * <p>它是各个 {@link AgentToolDefinition} 对外暴露的统一载体，也是
 * {@link AgentToolDefinitionSource} 的核心返回类型。</p>
 */
public record AgentToolDescriptor(
        String toolCode,
        ToolPresentation presentation,
        ToolExposure exposure,
        ToolGovernancePolicy governancePolicy
) {
}
