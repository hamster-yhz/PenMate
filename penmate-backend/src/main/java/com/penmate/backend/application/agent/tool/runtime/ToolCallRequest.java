package com.penmate.backend.application.agent.tool.runtime;

/**
 * 统一的 tool 调用运行时请求。
 * <p>该对象不仅承载 toolCode 与参数，还携带审批恢复所需的会话消息快照、tool call 标识、幂等键、loop 轮次等运行时上下文。</p>
 * <p>因此它不是面向模型的 schema DTO，而是 application/tool/runtime 层内部使用的执行载体。</p>
 */
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
