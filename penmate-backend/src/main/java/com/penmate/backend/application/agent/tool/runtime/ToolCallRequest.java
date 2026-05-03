package com.penmate.backend.application.agent.tool.runtime;

public record ToolCallRequest(
        Long projectId,
        Long taskId,
        Long conversationId,
        String toolCode,
        String toolArgsJson,
        Long operatorId,
        String traceId,
        String contextJson,
        String idempotencyKey,
        String loopRunId,
        Integer llmTurnIndex,
        String toolCallId,
        String assistantToolCallsJson,
        String conversationMessagesJson,
        String resumeMode,
        String approvalSummaryJson
) {

    public ToolCallRequest(Long projectId,
                           Long taskId,
                           Long conversationId,
                           String toolCode,
                           String toolArgsJson,
                           Long operatorId,
                           String traceId,
                           String contextJson,
                           String idempotencyKey) {
        this(projectId,
                taskId,
                conversationId,
                toolCode,
                toolArgsJson,
                operatorId,
                traceId,
                contextJson,
                idempotencyKey,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
