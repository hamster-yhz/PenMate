package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;

/**
 * Tool 内部某个 operation 的治理覆盖策略。
 * <p>它允许一个复合型 tool 在默认决策之外，为特定操作声明单独审批要求。</p>
 */
public record ToolOperationPolicy(
        String operationCode,
        ApprovalPolicyDecision decision
) {
}
