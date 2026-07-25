package com.penmate.backend.application.agent.tool.runtime;

import java.util.Objects;

/**
 * Internal tool execution envelope. Resource identity is intentionally limited to the Run and
 * is resolved into {@link AuthorizedAgentRunContext} before policy checks or handler execution.
 */
public record ToolCallRequest(
        Long runId,
        String toolCode,
        String toolArgsJson,
        String idempotencyKey,
        Integer llmTurnIndex,
        String toolCallId,
        String continuationJson,
        String assistantToolCallsJson,
        String conversationMessagesJson,
        String resumeMode,
        String approvalSummaryJson,
        Long executionToken
) {
    public ToolCallRequest {
        toolArgsJson = toolArgsJson == null || toolArgsJson.isBlank() ? "{}" : toolArgsJson;
        idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? Objects.toString(runId, "unknown") + ":" + Objects.toString(toolCallId, "unknown")
                : idempotencyKey;
    }

    public ToolCallRequest(Long runId,
                           String toolCode,
                           String toolArgsJson,
                           String idempotencyKey,
                           String toolCallId,
                           Long executionToken) {
        this(runId, toolCode, toolArgsJson, idempotencyKey, null, toolCallId,
                null, null, null, null, null, executionToken);
    }
}
