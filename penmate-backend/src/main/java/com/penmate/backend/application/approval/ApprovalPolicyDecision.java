package com.penmate.backend.application.approval;

public record ApprovalPolicyDecision(
        boolean approvalRequired,
        String approvalType
) {
}
