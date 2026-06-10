package com.penmate.backend.application.agent.tool.gateway;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalView;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalViewFactory;
import com.penmate.backend.application.agent.tool.handler.AgentToolHandler;
import com.penmate.backend.application.agent.tool.runtime.ToolCallExecutionService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import com.penmate.backend.application.approval.DefaultApprovalPolicyEngine;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ToolCallApplicationService {

    private final AgentToolDefinitionSource toolDefinitionSource;
    private final DefaultApprovalPolicyEngine approvalPolicyEngine;
    private final ToolApprovalViewFactory toolApprovalViewFactory;
    private final ApprovalApplicationService approvalApplicationService;
    private final AgentRunPendingApprovalRepository pendingApprovalRepository;
    private final ToolCallExecutionService toolCallExecutionService;

    public ToolCallApplicationService(AgentToolDefinitionSource toolDefinitionSource,
                                      DefaultApprovalPolicyEngine approvalPolicyEngine,
                                      ToolApprovalViewFactory toolApprovalViewFactory,
                                      ApprovalApplicationService approvalApplicationService,
                                      AgentRunPendingApprovalRepository pendingApprovalRepository,
                                      ToolCallExecutionService toolCallExecutionService) {
        this.toolDefinitionSource = toolDefinitionSource;
        this.approvalPolicyEngine = approvalPolicyEngine;
        this.toolApprovalViewFactory = toolApprovalViewFactory;
        this.approvalApplicationService = approvalApplicationService;
        this.pendingApprovalRepository = pendingApprovalRepository;
        this.toolCallExecutionService = toolCallExecutionService;
    }

    public ToolCallResult executeToolCall(ToolCallRequest request) {
        log.info("agent.tool.call.start: toolCode={}, projectId={}, runId={}, sessionId={}, traceId={}",
                request.toolCode(), request.projectId(), request.runId(), request.sessionId(), request.traceId());
        AgentToolDescriptor descriptor = toolDefinitionSource.getRequired(request.toolCode());
        ApprovalPolicyDecision decision = approvalPolicyEngine.evaluate(descriptor, request);
        String operationCode = extractOperationCode(request);
        if (decision.approvalRequired()) {
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
                    approvalRequest.getId(),
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
            log.info("agent.tool.call.waiting_approval: toolCode={}, operationCode={}, approvalId={}, runId={}, traceId={}",
                    request.toolCode(), operationCode, approvalRequest.getId(), request.runId(), request.traceId());
            return ToolCallResult.waitingApproval(approvalRequest.getId());
        }
        ToolCallResult result = toolCallExecutionService.execute(request);
        if (result != null && "SUCCESS".equals(result.status())) {
            log.info("agent.tool.call.success: toolCode={}, operationCode={}, runId={}, traceId={}",
                    request.toolCode(), operationCode, request.runId(), request.traceId());
        } else {
            log.warn("agent.tool.call.failed: toolCode={}, operationCode={}, status={}, errorCode={}, runId={}, traceId={}",
                    request.toolCode(), operationCode,
                    result == null ? null : result.status(),
                    result == null ? null : result.errorCode(),
                    request.runId(), request.traceId());
        }
        return result;
    }

    private String extractOperationCode(ToolCallRequest request) {
        try {
            JSONObject args = AgentJsonCodec.parseObj(request.toolArgsJson());
            String operation = args.getStr("operation", null);
            return operation == null || operation.isBlank() ? null : operation.trim();
        } catch (Exception ex) {
            return null;
        }
    }
}
