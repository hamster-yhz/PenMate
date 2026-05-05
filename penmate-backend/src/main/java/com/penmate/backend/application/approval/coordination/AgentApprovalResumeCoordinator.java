package com.penmate.backend.application.approval.coordination;

import com.penmate.backend.application.agent.tool.runtime.ToolCallResumeService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.approval.ApprovalAgentResumeCoordinator;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentApprovalResumeCoordinator implements ApprovalAgentResumeCoordinator {

    private final ToolCallResumeService toolCallResumeService;

    @Override
    public ToolCallResult resumeApprovedInvocation(ApprovalRequest request, PendingToolInvocationSnapshot snapshot) {
        return toolCallResumeService.resumeFromPending(request, snapshot);
    }
}
