package com.penmate.backend.application.approval.command;

public record ReviewApprovalCommand(
        Long reviewedBy,
        String comment
) {
}

