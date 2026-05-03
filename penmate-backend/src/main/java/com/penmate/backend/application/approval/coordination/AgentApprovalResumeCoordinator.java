package com.penmate.backend.application.approval.coordination;

import com.penmate.backend.application.agent.orchestration.AgentToolLoopRunner;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.approval.ApprovalAgentResumeCoordinator;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentApprovalResumeCoordinator implements ApprovalAgentResumeCoordinator {

    private final ToolCallApplicationService toolCallApplicationService;
    private final AgentToolLoopRunner agentToolLoopRunner;

    @Override
    public ToolCallResult resumeApprovedInvocation(ApprovalRequest request, PendingToolInvocationSnapshot snapshot) {
        if ("RESUME_LOOP".equals(snapshot.resumeMode())) {
            return agentToolLoopRunner.resumeFromPending(request, snapshot);
        }
        return toolCallApplicationService.executeToolCall(new ToolCallRequest(
                snapshot.projectId(),
                snapshot.taskId(),
                snapshot.conversationId(),
                snapshot.toolCode(),
                snapshot.toolArgsJson(),
                snapshot.operatorId(),
                snapshot.traceId(),
                snapshot.contextJson(),
                snapshot.idempotencyKey(),
                snapshot.loopRunId(),
                snapshot.llmTurnIndex(),
                snapshot.toolCallId(),
                snapshot.assistantToolCallsJson(),
                snapshot.conversationMessagesJson(),
                snapshot.resumeMode(),
                snapshot.approvalSummaryJson()
        ));
    }
}
