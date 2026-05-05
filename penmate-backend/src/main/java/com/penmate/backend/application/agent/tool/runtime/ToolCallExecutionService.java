package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.application.agent.tool.handler.AgentToolHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent tool 直接执行器。
 * <p>该组件只负责解析 handler、执行参数校验并调用具体 handler，
 * 不承担审批、挂起、恢复编排等治理职责。</p>
 */
@Component
@Slf4j
public class ToolCallExecutionService {

    private final List<AgentToolHandler> handlers;

    public ToolCallExecutionService(List<AgentToolHandler> handlers) {
        this.handlers = handlers;
    }

    public ToolCallResult execute(ToolCallRequest request) {
        java.util.Optional<AgentToolHandler> handler = findHandler(request.toolCode());
        if (handler.isEmpty()) {
            log.warn("tool 调用失败: toolCode={}, reason=handler_not_found, traceId={}", request.toolCode(), request.traceId());
            return new ToolCallResult(
                    "FAILED",
                    null,
                    null,
                    "TOOL_HANDLER_NOT_FOUND",
                    "Tool handler not found: " + request.toolCode()
            );
        }
        try {
            handler.get().validate(request);
        } catch (IllegalArgumentException ex) {
            log.warn("tool 调用校验失败: toolCode={}, traceId={}, message={}", request.toolCode(), request.traceId(), ex.getMessage());
            return new ToolCallResult(
                    "FAILED",
                    null,
                    null,
                    "TOOL_VALIDATION_FAILED",
                    ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage()
            );
        }
        return handler.get().execute(request);
    }

    private java.util.Optional<AgentToolHandler> findHandler(String toolCode) {
        return handlers.stream()
                .filter(handler -> java.util.Objects.equals(handler.toolCode(), toolCode))
                .findFirst();
    }
}
