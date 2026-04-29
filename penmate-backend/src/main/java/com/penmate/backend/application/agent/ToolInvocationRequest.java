package com.penmate.backend.application.agent;

public record ToolInvocationRequest(
        Long projectId,
        Long taskId,
        Long conversationId,
        String toolCode,
        String toolArgsJson,
        Long operatorId,
        String traceId,
        String contextJson,
        String idempotencyKey
) {
}
