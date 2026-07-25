package com.penmate.backend.application.agent.tool.gateway;

import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalView;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalViewFactory;
import com.penmate.backend.application.agent.tool.runtime.AgentRunExecutionContextResolver;
import com.penmate.backend.application.agent.tool.runtime.AgentRunExecutionRejectedException;
import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallExecutionService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.agent.tool.runtime.ToolApprovalPreview;
import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import com.penmate.backend.application.approval.DefaultApprovalPolicyEngine;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
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
    private final ToolApprovalPreview toolApprovalPreview;
    private final JsonCodec jsonCodec;
    private final AgentRunExecutionContextResolver executionContexts;

    public ToolCallApplicationService(AgentToolDefinitionSource toolDefinitionSource,
                                      DefaultApprovalPolicyEngine approvalPolicyEngine,
                                      ToolApprovalViewFactory toolApprovalViewFactory,
                                      ApprovalApplicationService approvalApplicationService,
                                      AgentRunPendingApprovalRepository pendingApprovalRepository,
                                      ToolCallExecutionService toolCallExecutionService,
                                      ToolApprovalPreview toolApprovalPreview,
                                      JsonCodec jsonCodec,
                                      AgentRunExecutionContextResolver executionContexts) {
        this.toolDefinitionSource = toolDefinitionSource;
        this.approvalPolicyEngine = approvalPolicyEngine;
        this.toolApprovalViewFactory = toolApprovalViewFactory;
        this.approvalApplicationService = approvalApplicationService;
        this.pendingApprovalRepository = pendingApprovalRepository;
        this.toolCallExecutionService = toolCallExecutionService;
        this.toolApprovalPreview = toolApprovalPreview;
        this.jsonCodec = jsonCodec;
        this.executionContexts = executionContexts;
    }

    public ToolCallResult executeToolCall(ToolCallRequest request) {
        AuthorizedAgentRunContext context;
        try {
            context = executionContexts.resolve(request);
        } catch (AgentRunExecutionRejectedException rejection) {
            return ToolCallResult.failed(rejection.errorCode(), rejection.getMessage());
        }
        log.info("agent.tool.call.start: toolCode={}, projectId={}, runId={}, sessionId={}, traceId={}",
                request.toolCode(), context.projectId(), context.runId(), context.sessionId(), context.traceId());
        AgentToolDescriptor descriptor = toolDefinitionSource.getRequired(request.toolCode());
        ApprovalPolicyDecision decision = approvalPolicyEngine.evaluate(descriptor, request);
        String operationCode = extractOperationCode(request);
        if (decision.approvalRequired()) {
            var approvalPreview = toolApprovalPreview.from(request.toolCode(), request.toolArgsJson());
            ToolCallResult validationFailure = toolCallExecutionService.validate(context, request);
            if (validationFailure != null) return validationFailure;
            AgentRunPendingApproval existing = pendingApprovalRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing != null) {
                if (!matches(existing, context, request)) {
                    return ToolCallResult.failed("TOOL_CALL_REQUEST_MISMATCH",
                            "Tool approval idempotency key was already used by another request");
                }
                if ("PENDING".equals(existing.pendingStatus())
                        || "APPROVED".equals(existing.pendingStatus())
                        || "RESUMING".equals(existing.pendingStatus())) {
                    return ToolCallResult.waitingApproval(existing.approvalId(), approvalPreview);
                }
                return ToolCallResult.failed("TOOL_APPROVAL_NOT_EXECUTABLE",
                        "Existing tool approval is already terminal: " + existing.pendingStatus());
            }
            ToolApprovalView approvalView = toolApprovalViewFactory.create(descriptor, decision);
            ApprovalRequest approvalRequest = approvalApplicationService.create(new CreateApprovalCommand(
                    context.projectId(),
                    context.runId(),
                    decision.approvalType(),
                    request.toolArgsJson(),
                    approvalView.riskLevel() == null ? descriptor.governancePolicy().riskLevel() : approvalView.riskLevel(),
                    context.ownerUserId()
            ), context.traceId());
            pendingApprovalRepository.save(new AgentRunPendingApproval(
                    null,
                    approvalRequest.getApprovalRequestId(),
                    approvalRequest.getApprovalRequestId(),
                    context.runId(),
                    context.projectId(),
                    context.sessionId(),
                    context.turnId(),
                    request.toolCallId(),
                    request.toolCode(),
                    request.toolArgsJson(),
                    request.continuationJson(),
                    request.conversationMessagesJson(),
                    request.idempotencyKey(),
                    "PENDING",
                    context.ownerUserId(),
                    context.traceId(),
                    null,
                    null
            ));
            log.info("agent.tool.call.waiting_approval: toolCode={}, operationCode={}, approvalId={}, runId={}, traceId={}",
                    request.toolCode(), operationCode, approvalRequest.getApprovalRequestId(), context.runId(), context.traceId());
            return ToolCallResult.waitingApproval(approvalRequest.getApprovalRequestId(), approvalPreview);
        }
        ToolCallResult result = toolCallExecutionService.execute(context, request);
        if (result != null && "SUCCESS".equals(result.status())) {
            log.info("agent.tool.call.success: toolCode={}, operationCode={}, runId={}, traceId={}",
                    request.toolCode(), operationCode, context.runId(), context.traceId());
        } else {
            log.warn("agent.tool.call.failed: toolCode={}, operationCode={}, status={}, errorCode={}, runId={}, traceId={}",
                    request.toolCode(), operationCode,
                    result == null ? null : result.status(),
                    result == null ? null : result.errorCode(),
                    context.runId(), context.traceId());
        }
        return result;
    }

    private String extractOperationCode(ToolCallRequest request) {
        try {
            String operation = JsonValues.string(jsonCodec.readObject(request.toolArgsJson()), "operation");
            return operation == null || operation.isBlank() ? null : operation.trim();
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean matches(AgentRunPendingApproval pending, AuthorizedAgentRunContext context,
                            ToolCallRequest request) {
        return java.util.Objects.equals(pending.runId(), context.runId())
                && java.util.Objects.equals(pending.projectId(), context.projectId())
                && java.util.Objects.equals(pending.sessionId(), context.sessionId())
                && java.util.Objects.equals(pending.turnId(), context.turnId())
                && java.util.Objects.equals(pending.toolCallId(), request.toolCallId())
                && java.util.Objects.equals(pending.toolCode(), request.toolCode())
                && java.util.Objects.equals(pending.toolArgsJson(), request.toolArgsJson());
    }
}
