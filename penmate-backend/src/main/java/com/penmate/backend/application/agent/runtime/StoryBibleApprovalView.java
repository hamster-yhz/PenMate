package com.penmate.backend.application.agent.runtime;

import java.util.List;

/**
 * Story Bible 审核/确认运行态视图。
 */
public record StoryBibleApprovalView(
        Long approvalId,
        String approvalType,
        String proposalSummary,
        List<String> entryKeys,
        String nextAction
) {
}
