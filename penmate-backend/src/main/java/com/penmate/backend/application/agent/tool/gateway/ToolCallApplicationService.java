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
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import cn.hutool.json.JSONObject;

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
    private final ToolCallExecutionService toolCallExecutionService;

    public ToolCallApplicationService(AgentToolDefinitionSource toolDefinitionSource,
                                      DefaultApprovalPolicyEngine approvalPolicyEngine,
                                      ToolApprovalViewFactory toolApprovalViewFactory,
                                      ApprovalApplicationService approvalApplicationService,
                                      PendingToolInvocationRepository pendingToolInvocationRepository,
                                      AgentRepository agentRepository,
                                      ToolCallExecutionService toolCallExecutionService) {
        this.toolDefinitionSource = toolDefinitionSource;
        this.approvalPolicyEngine = approvalPolicyEngine;
        this.toolApprovalViewFactory = toolApprovalViewFactory;
        this.approvalApplicationService = approvalApplicationService;
        this.pendingToolInvocationRepository = pendingToolInvocationRepository;
        this.agentRepository = agentRepository;
        this.toolCallExecutionService = toolCallExecutionService;
    }

    public ToolCallResult executeToolCall(ToolCallRequest request) {
        log.info("发起 tool 调用: toolCode={}, projectId={}, taskId={}, conversationId={}, traceId={}",
                request.toolCode(), request.projectId(), request.taskId(), request.conversationId(), request.traceId());
        AgentToolDescriptor descriptor = toolDefinitionSource.getRequired(request.toolCode());
        ApprovalPolicyDecision decision = approvalPolicyEngine.evaluate(descriptor, request);
        log.info("tool 审批决策完成: toolCode={}, approvalRequired={}, approvalType={}, traceId={}",
                request.toolCode(), decision.approvalRequired(), decision.approvalType(), request.traceId());
        String operationCode = extractOperationCode(request);
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
            agentRepository.updateGenerationTaskActiveApproval(request.projectId(), request.taskId(), approvalRequest.getId());
            log.info("tool 调用进入待审批: toolCode={}, operationCode={}, approvalId={}, taskId={}, traceId={}",
                    request.toolCode(), operationCode, approvalRequest.getId(), request.taskId(), request.traceId());
            return ToolCallResult.waitingApproval(approvalRequest.getId());
        }
        ToolCallResult result = toolCallExecutionService.execute(request);
        if (result != null && "SUCCESS".equals(result.status())) {
            log.info("tool 调用成功: toolCode={}, operationCode={}, taskId={}, traceId={}",
                    request.toolCode(), operationCode, request.taskId(), request.traceId());
        } else {
            log.warn("tool 调用失败: toolCode={}, operationCode={}, status={}, errorCode={}, taskId={}, traceId={}",
                    request.toolCode(), operationCode,
                    result == null ? null : result.status(),
                    result == null ? null : result.errorCode(),
                    request.taskId(), request.traceId());
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

    private String resolveToolEventName(ToolCallRequest request,
                                        AgentToolDescriptor descriptor,
                                        String operationCode) {
        if ("draft_generation".equals(request.toolCode())) {
            if ("rewrite".equals(operationCode)) {
                return "改写正文";
            }
            if ("revise".equals(operationCode)) {
                return "套用修订";
            }
            return "生成正文";
        }
        if (descriptor != null && descriptor.presentation() != null
                && descriptor.presentation().displayName() != null
                && !descriptor.presentation().displayName().isBlank()) {
            return descriptor.presentation().displayName();
        }
        return request.toolCode();
    }
}
