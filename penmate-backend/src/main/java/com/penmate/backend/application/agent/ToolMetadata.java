package com.penmate.backend.application.agent;

public record ToolMetadata(
        String toolCode,
        String displayName,
        boolean approvalRequired,
        String approvalType,
        Integer riskLevel
) {
}
