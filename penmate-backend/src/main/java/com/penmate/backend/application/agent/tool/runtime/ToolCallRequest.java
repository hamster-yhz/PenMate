package com.penmate.backend.application.agent.tool.runtime;

/**
 * 统一的 tool 调用运行时请求。
 * <p>该对象不仅承载 toolCode 与参数，还携带审批恢复所需的会话消息快照、tool call 标识、幂等键、loop 轮次等运行时上下文。</p>
 * <p>因此它不是面向模型的 schema DTO，而是 application/tool/runtime 层内部使用的执行载体。</p>
 */
public record ToolCallRequest(
        Long projectId,
        Long runId,
        Long sessionId,
        Long turnId,
        String toolCode,
        String toolArgsJson,
        Long operatorId,
        String traceId,
        String contextJson,
        String idempotencyKey,
        Integer llmTurnIndex,
        String toolCallId,
        String assistantToolCallsJson,
        String conversationMessagesJson,
        String resumeMode,
        String approvalSummaryJson,
        Long executionToken
) {

    public ToolCallRequest(Long projectId,
                           Long runId,
                           Long sessionId,
                           Long turnId,
                           String toolCode,
                           String toolArgsJson,
                           Long operatorId,
                           String traceId,
                           String contextJson,
                           String idempotencyKey,
                           Integer llmTurnIndex,
                           String toolCallId,
                           String assistantToolCallsJson,
                           String conversationMessagesJson,
                           String resumeMode,
                           String approvalSummaryJson) {
        this(projectId, runId, sessionId, turnId, toolCode, toolArgsJson, operatorId, traceId,
                contextJson, idempotencyKey, llmTurnIndex, toolCallId, assistantToolCallsJson,
                conversationMessagesJson, resumeMode, approvalSummaryJson, null);
    }

    public ToolCallRequest(Long projectId,
                           Long runId,
                           Long sessionId,
                           String toolCode,
                           String toolArgsJson,
                           Long operatorId,
                           String traceId,
                           String contextJson,
                           String idempotencyKey) {
        this(projectId,
                runId,
                sessionId,
                null,
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

    public ToolCallRequest(Long projectId,
                           Long runId,
                           Long sessionId,
                           String toolCode,
                           String toolArgsJson,
                           Long operatorId,
                           String traceId,
                           String contextJson,
                           String idempotencyKey,
                           String ignoredLoopRunId,
                           Integer llmTurnIndex,
                           String toolCallId,
                           String assistantToolCallsJson,
                           String conversationMessagesJson,
                           String resumeMode,
                           String approvalSummaryJson) {
        this(projectId,
                runId,
                sessionId,
                null,
                toolCode,
                toolArgsJson,
                operatorId,
                traceId,
                contextJson,
                idempotencyKey,
                llmTurnIndex,
                toolCallId,
                assistantToolCallsJson,
                conversationMessagesJson,
                resumeMode,
                approvalSummaryJson,
                null);
    }
}
