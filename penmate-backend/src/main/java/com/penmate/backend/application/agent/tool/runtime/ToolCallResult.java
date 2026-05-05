package com.penmate.backend.application.agent.tool.runtime;

/**
 * tool 调用统一结果。
 * <p>该结果同时覆盖三类状态：执行成功、执行失败、等待审批。调用方需结合 {@code status} 决定是否继续 loop、挂起任务或终止流程。</p>
 */
public record ToolCallResult(
        String status,
        Long approvalId,
        String toolOutput,
        String errorCode,
        String errorMessage
) {
    public static ToolCallResult waitingApproval(Long approvalId) {
        return new ToolCallResult("WAITING_APPROVAL", approvalId, null, null, null);
    }

    public static ToolCallResult success(String toolOutput) {
        return new ToolCallResult("SUCCESS", null, toolOutput, null, null);
    }
}
