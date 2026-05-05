package com.penmate.backend.application.agent.tool.definition;

import com.penmate.backend.application.approval.ApprovalPolicyDecision;

import java.util.Map;

/**
 * Tool 治理策略。
 * <p>包含工具级默认审批决策、风险等级，以及按操作码细分的覆盖策略。</p>
 * <p>当某个 tool 同时承载多个业务操作时，可通过 {@code operationPolicies} 对默认策略做更细粒度调整。</p>
 */
public record ToolGovernancePolicy(
        ApprovalPolicyDecision defaultDecision,
        Integer riskLevel,
        Map<String, ToolOperationPolicy> operationPolicies
) {
}
