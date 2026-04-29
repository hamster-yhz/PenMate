package com.penmate.backend.application.agent;

public interface AgentToolHandler {

    String toolCode();

    default void validate(ToolInvocationRequest request) {
        // 默认无额外校验
    }

    ToolInvocationGatewayResult execute(ToolInvocationRequest request);
}
