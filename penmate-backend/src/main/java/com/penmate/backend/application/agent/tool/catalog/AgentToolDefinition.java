package com.penmate.backend.application.agent.tool.catalog;

/**
 * Agent tool 元数据定义。
 * <p>该记录只描述应用层治理所需的稳定元数据，不直接等同于面向模型暴露的 tool schema。</p>
 * <p>其中 {@code approvalRequired}、{@code approvalType} 与 {@code riskLevel} 主要供
 * {@link com.penmate.backend.application.approval.DefaultApprovalPolicyEngine} 和
 * {@link com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService} 使用。</p>
 */
public record AgentToolDefinition(
        String toolCode,
        String displayName,
        boolean approvalRequired,
        String approvalType,
        Integer riskLevel
) {
}
