package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.approval.model.ApprovalRequest;

/**
 * Agent/approval 之间的审批通过后恢复协调边界。
 */
public interface ApprovalAgentResumeCoordinator {

    ToolCallResult resumeApprovedInvocation(ApprovalRequest request, PendingToolInvocationSnapshot snapshot);
}
