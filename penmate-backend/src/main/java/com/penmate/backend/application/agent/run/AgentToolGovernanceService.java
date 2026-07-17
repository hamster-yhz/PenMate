package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalView;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalViewFactory;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import com.penmate.backend.application.approval.DefaultApprovalPolicyEngine;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.springframework.stereotype.Service;

@Service
public class AgentToolGovernanceService {

    private final AgentToolDefinitionSource toolDefinitionSource;
    private final DefaultApprovalPolicyEngine approvalPolicyEngine;
    private final ToolApprovalViewFactory toolApprovalViewFactory;
    private final ApprovalApplicationService approvalApplicationService;
    private final AgentRunPendingApprovalRepository pendingApprovalRepository;

    public AgentToolGovernanceService(AgentToolDefinitionSource toolDefinitionSource,
                                      DefaultApprovalPolicyEngine approvalPolicyEngine,
                                      ToolApprovalViewFactory toolApprovalViewFactory,
                                      ApprovalApplicationService approvalApplicationService,
                                      AgentRunPendingApprovalRepository pendingApprovalRepository) {
        this.toolDefinitionSource = toolDefinitionSource;
        this.approvalPolicyEngine = approvalPolicyEngine;
        this.toolApprovalViewFactory = toolApprovalViewFactory;
        this.approvalApplicationService = approvalApplicationService;
        this.pendingApprovalRepository = pendingApprovalRepository;
    }

    public AgentToolGovernanceDecision beforeExecute(ToolCallRequest request) {
        AgentToolDescriptor descriptor = toolDefinitionSource.getRequired(request.toolCode());
        ApprovalPolicyDecision decision = approvalPolicyEngine.evaluate(descriptor, request);
        if (!decision.approvalRequired()) {
            return AgentToolGovernanceDecision.allowed();
        }

        ToolApprovalView approvalView = toolApprovalViewFactory.create(descriptor, decision);
        ApprovalRequest approvalRequest = approvalApplicationService.create(new CreateApprovalCommand(
                request.projectId(),
                request.runId(),
                decision.approvalType(),
                request.toolArgsJson(),
                approvalView.riskLevel() == null ? descriptor.governancePolicy().riskLevel() : approvalView.riskLevel(),
                request.operatorId()
        ), request.traceId());

        pendingApprovalRepository.save(new AgentRunPendingApproval(
                null,
                approvalRequest.getApprovalRequestId(),
                approvalRequest.getApprovalRequestId(),
                request.runId(),
                request.projectId(),
                request.sessionId(),
                request.turnId(),
                request.toolCallId(),
                request.toolCode(),
                request.toolArgsJson(),
                request.contextJson(),
                AgentJsonCodec.toJson(approvalView),
                request.idempotencyKey(),
                "PENDING",
                request.operatorId(),
                request.traceId(),
                null,
                null
        ));
        return AgentToolGovernanceDecision.waitingApproval(approvalRequest.getApprovalRequestId());
    }
}
