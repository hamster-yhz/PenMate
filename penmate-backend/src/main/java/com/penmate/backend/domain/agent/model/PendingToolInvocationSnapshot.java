package com.penmate.backend.domain.agent.model;

/**
 * 待恢复 tool 调用快照。
 * <p>
 * 当一次高风险工具调用需要等待人工审批时，系统会把恢复执行所需的完整上下文保存为本快照。
 * 审批通过后，应用服务可依据 {@code approvalId} 取回该记录，并恢复为原始 tool 请求继续执行。
 * </p>
 *
 * @param approvalId 关联审批单 ID，用于从审批结果回查挂起调用
 * @param projectId 调用所属项目 ID
 * @param taskId 调用所属生成任务 ID
 * @param conversationId 调用所属会话 ID
 * @param toolCode 被调用 tool 的唯一编码
 * @param toolArgsJson 原始 tool 参数 JSON
 * @param contextJson 原始调用上下文 JSON
 * @param operatorId 原始调用发起人 ID
 * @param traceId 首次调用链路的 traceId
 * @param idempotencyKey 首次调用所使用的幂等键
 * @param status 快照当前状态；当前实现按字符串约定使用 pending、executing、completed、failed 等值，
 *               但这些状态尚未被类型系统封装为独立领域枚举
 */
public record PendingToolInvocationSnapshot(
        Long approvalId,
        Long projectId,
        Long taskId,
        Long conversationId,
        String toolCode,
        String toolArgsJson,
        String contextJson,
        Long operatorId,
        String traceId,
        String idempotencyKey,
        String status
) {
}
