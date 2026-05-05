package com.penmate.backend.application.agent.tool.definition;

/**
 * Agent tool definition 单元。
 * <p>每个 tool 以独立类声明自己的 descriptor，作为 schema、展示信息与治理策略的单一真源。</p>
 * <p>{@link InMemoryAgentToolDefinitionSource} 只负责聚合这些 definition，并提供按 toolCode 查询与面向 LLM 的 schema 列表。</p>
 */
public interface AgentToolDefinition {

    /**
     * 返回当前 tool 的完整 descriptor。
     *
     * @return tool descriptor
     */
    AgentToolDescriptor descriptor();
}
