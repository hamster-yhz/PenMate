package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;

/**
 * Agent tool 执行处理器抽象。
 * <p>该接口位于 tool 执行层，只负责两件事：声明自身处理的 {@code toolCode}，以及对对应 tool call
 * 执行参数校验与具体业务动作。</p>
 * <p>审批、挂起、恢复、幂等键和任务状态流转不应在实现类中处理，这些治理职责统一由
 * {@link com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService} 承担。</p>
 */
public interface AgentToolHandler {

    String toolCode();

    default boolean mutatesState(AuthorizedAgentRunContext context, ToolCallRequest request) {
        return false;
    }

    /**
     * 对 tool 调用请求执行参数级校验。
     *
     * @param request 当前 tool 调用请求
     * @throws IllegalArgumentException 当参数结构非法或缺少必要字段时抛出
     */
    default void validate(AuthorizedAgentRunContext context, ToolCallRequest request) {
        // 默认无额外校验
    }

    /**
     * 执行当前 handler 负责的具体 tool 业务动作。
     *
     * @param request 当前 tool 调用请求
     * @return tool 执行结果；成功、失败或等待审批由上层调用方按约定解释
     */
    ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request);
}
