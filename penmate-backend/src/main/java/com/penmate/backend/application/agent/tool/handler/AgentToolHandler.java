package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;

public interface AgentToolHandler {

    String toolCode();

    default void validate(ToolCallRequest request) {
        // 默认无额外校验
    }

    ToolCallResult execute(ToolCallRequest request);
}
