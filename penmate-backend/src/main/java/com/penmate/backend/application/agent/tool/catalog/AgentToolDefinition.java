package com.penmate.backend.application.agent.tool.catalog;

public record AgentToolDefinition(
        String toolCode,
        String displayName,
        boolean approvalRequired,
        String approvalType,
        Integer riskLevel
) {
}
