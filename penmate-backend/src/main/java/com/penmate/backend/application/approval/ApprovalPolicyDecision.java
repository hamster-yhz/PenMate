package com.penmate.backend.application.approval;

public record ApprovalPolicyDecision(
        boolean approvalRequired,
        String approvalType,
        Integer riskLevel,
        String operationCode,
        String displayName
) {

    public ApprovalPolicyDecision(boolean approvalRequired, String approvalType) {
        this(approvalRequired, approvalType, null, null, null);
    }
}
