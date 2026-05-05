package com.penmate.backend.application.agent.tool.gateway;

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
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent tool 调用治理应用服务。
 * <p>该类位于 tool gateway / governance 层，统一编排以下职责：</p>
 * <ol>
 *   <li>按 {@code toolCode} 读取 {@link AgentToolDescriptor} 真源元数据；</li>
 *   <li>定位对应 {@link AgentToolHandler} 并执行参数校验；</li>
 *   <li>调用审批策略引擎判断是否需要人工审批；</li>
 *   <li>命中审批时创建审批单、保存 {@link PendingToolInvocationSnapshot} 并挂起任务；</li>
 *   <li>无需审批时把请求分发给具体 handler 执行。</li>
 * </ol>
 * <p>因此它不是某个单纯的 tool executor，而是贯穿“执行前治理、挂起与恢复协议”的应用层入口。</p>
 */
@Component
@Slf4j
public class ToolCallApplicationService {

    private final AgentToolDefinitionSource toolDefinitionSource;
    private final DefaultApprovalPolicyEngine approvalPolicyEngine;
    private final ToolApprovalViewFactory toolApprovalViewFactory;
    private final ApprovalApplicationService approvalApplicationService;
    private final PendingToolInvocationRepository pendingToolInvocationRepository;
    private final AgentRepository agentRepository;
    private final RealtimeEventService realtimeEventService;
    private final ToolCallExecutionService toolCallExecutionService;

    public ToolCallApplicationService(AgentToolDefinitionSource toolDefinitionSource,
                                      DefaultApprovalPolicyEngine approvalPolicyEngine,
                                      ToolApprovalViewFactory toolApprovalViewFactory,
                                      ApprovalApplicationService approvalApplicationService,
                                      PendingToolInvocationRepository pendingToolInvocationRepository,
                                      AgentRepository agentRepository,
                                      RealtimeEventService realtimeEventService,
                                      ToolCallExecutionService toolCallExecutionService) {
        this.toolDefinitionSource = toolDefinitionSource;
        this.approvalPolicyEngine = approvalPolicyEngine;
        this.toolApprovalViewFactory = toolApprovalViewFactory;
        this.approvalApplicationService = approvalApplicationService;
        this.pendingToolInvocationRepository = pendingToolInvocationRepository;
        this.agentRepository = agentRepository;
        this.realtimeEventService = realtimeEventService;
        this.toolCallExecutionService = toolCallExecutionService;
    }

    public ToolCallResult executeToolCall(ToolCallRequest request) {
        log.info("发起 tool 调用: toolCode={}, projectId={}, taskId={}, conversationId={}, traceId={}",
                request.toolCode(), request.projectId(), request.taskId(), request.conversationId(), request.traceId());
        AgentToolDescriptor descriptor = toolDefinitionSource.getRequired(request.toolCode());
        ApprovalPolicyDecision decision = approvalPolicyEngine.evaluate(descriptor, request);
        log.info("tool 审批决策完成: toolCode={}, approvalRequired={}, approvalType={}, traceId={}",
                request.toolCode(), decision.approvalRequired(), decision.approvalType(), request.traceId());
        if (decision.approvalRequired()) {
            ToolApprovalView approvalView = toolApprovalViewFactory.create(descriptor, decision);
            ApprovalRequest approvalRequest = approvalApplicationService.create(new CreateApprovalCommand(
                    request.projectId(),
                    request.taskId(),
                    decision.approvalType(),
                    request.toolArgsJson(),
                    approvalView.riskLevel() == null ? descriptor.governancePolicy().riskLevel() : approvalView.riskLevel(),
                    request.operatorId()
            ), request.traceId());
            String resumeMode = request.resumeMode() == null || request.resumeMode().isBlank()
                    ? "RESUME_LOOP"
                    : request.resumeMode();
            String approvalSummaryJson = request.approvalSummaryJson() == null || request.approvalSummaryJson().isBlank()
                    ? AgentJsonCodec.toJson(approvalView)
                    : request.approvalSummaryJson();
            pendingToolInvocationRepository.save(new PendingToolInvocationSnapshot(
                    approvalRequest.getId(),
                    request.projectId(),
                    request.taskId(),
                    request.conversationId(),
                    request.toolCode(),
                    request.toolArgsJson(),
                    request.contextJson(),
                    request.operatorId(),
                    request.traceId(),
                    request.idempotencyKey(),
                    "pending",
                    request.loopRunId(),
                    request.llmTurnIndex(),
                    request.toolCallId(),
                    request.assistantToolCallsJson(),
                    request.conversationMessagesJson(),
                    resumeMode,
                    approvalSummaryJson
            ));
            agentRepository.updateGenerationTaskStatus(request.projectId(), request.taskId(), "waiting_approval", null);
            realtimeEventService.publishGenerationWaitingApproval(
                    request.projectId(),
                    request.taskId(),
                    request.toolCallId(),
                    approvalRequest.getId(),
                    decision.approvalType(),
                    approvalView,
                    resumeMode,
                    approvalView
            );
            return ToolCallResult.waitingApproval(approvalRequest.getId());
        }
        return toolCallExecutionService.execute(request);
    }
}
