package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;

import java.util.List;

/**
 * Agent tool 定义读取入口。
 * <p>调用方通过该接口读取指定 tool 的 descriptor，或取得当前允许暴露给 LLM 的 schema 列表。</p>
 * <p>接口本身不关心 definition 来自注解扫描、内存注册还是其他装配方式，只约束读取语义。</p>
 */
public interface AgentToolDefinitionSource {

    /**
     * 按 toolCode 读取 tool descriptor，不存在时抛异常。
     *
     * @param toolCode tool 标识
     * @return 对应 descriptor
     */
    AgentToolDescriptor getRequired(String toolCode);

    /**
     * 返回全部已注册 descriptor。
     *
     * @return 全量 descriptor 列表
     */
    List<AgentToolDescriptor> listAll();

    /**
     * 列出当前暴露给 LLM 的 tool schema。
     *
     * @return LLM 可见的 tool schema 列表
     */
    List<AgentLlmToolSchema> listLlmSchemas();
}
