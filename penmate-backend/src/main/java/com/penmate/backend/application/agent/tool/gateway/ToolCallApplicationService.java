package com.penmate.backend.application.agent.tool.gateway;

import com.penmate.backend.application.agent.tool.catalog.AgentToolDefinition;
import com.penmate.backend.application.agent.tool.catalog.StaticAgentToolCatalog;
import com.penmate.backend.application.agent.tool.handler.AgentToolHandler;
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
 *   <li>按 {@code toolCode} 读取 {@link AgentToolDefinition} 元数据；</li>
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

    private final StaticAgentToolCatalog toolCatalog;
    private final DefaultApprovalPolicyEngine approvalPolicyEngine;
    private final ApprovalApplicationService approvalApplicationService;
    private final PendingToolInvocationRepository pendingToolInvocationRepository;
    private final AgentRepository agentRepository;
    private final RealtimeEventService realtimeEventService;
    private final List<AgentToolHandler> handlers;

    public ToolCallApplicationService(StaticAgentToolCatalog toolCatalog,
                                      DefaultApprovalPolicyEngine approvalPolicyEngine,
                                      ApprovalApplicationService approvalApplicationService,
                                      PendingToolInvocationRepository pendingToolInvocationRepository,
                                      AgentRepository agentRepository,
                                      RealtimeEventService realtimeEventService,
                                      List<AgentToolHandler> handlers) {
        this.toolCatalog = toolCatalog;
        this.approvalPolicyEngine = approvalPolicyEngine;
        this.approvalApplicationService = approvalApplicationService;
        this.pendingToolInvocationRepository = pendingToolInvocationRepository;
        this.agentRepository = agentRepository;
        this.realtimeEventService = realtimeEventService;
        this.handlers = handlers;
    }

    public ToolCallResult executeToolCall(ToolCallRequest request) {
        log.info("发起 tool 调用: toolCode={}, projectId={}, taskId={}, conversationId={}, traceId={}",
                request.toolCode(), request.projectId(), request.taskId(), request.conversationId(), request.traceId());
        AgentToolDefinition metadata = toolCatalog.getRequired(request.toolCode());
        java.util.Optional<AgentToolHandler> handler = findHandler(request.toolCode());
        if (handler.isEmpty()) {
            log.warn("tool 调用失败: toolCode={}, reason=handler_not_found, traceId={}", request.toolCode(), request.traceId());
            return new ToolCallResult(
                    "FAILED",
                    null,
                    null,
                    "TOOL_HANDLER_NOT_FOUND",
                    "Tool handler not found: " + request.toolCode()
            );
        }
        try {
            handler.get().validate(request);
        } catch (IllegalArgumentException ex) {
            log.warn("tool 调用校验失败: toolCode={}, traceId={}, message={}", request.toolCode(), request.traceId(), ex.getMessage());
            return new ToolCallResult(
                    "FAILED",
                    null,
                    null,
                    "TOOL_VALIDATION_FAILED",
                    ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage()
            );
        }
        ApprovalPolicyDecision decision = approvalPolicyEngine.evaluate(metadata, request);
        log.info("tool 审批决策完成: toolCode={}, approvalRequired={}, approvalType={}, traceId={}",
                request.toolCode(), decision.approvalRequired(), decision.approvalType(), request.traceId());
        if (decision.approvalRequired()) {
            ApprovalRequest approvalRequest = approvalApplicationService.create(new CreateApprovalCommand(
                    request.projectId(),
                    request.taskId(),
                    decision.approvalType(),
                    request.toolArgsJson(),
                    metadata.riskLevel(),
                    request.operatorId()
            ), request.traceId());
            String resumeMode = request.resumeMode() == null || request.resumeMode().isBlank()
                    ? "RESUME_LOOP"
                    : request.resumeMode();
            String approvalSummaryJson = request.approvalSummaryJson() == null || request.approvalSummaryJson().isBlank()
                    ? AgentJsonCodec.toJson(java.util.Map.of("approvalType", decision.approvalType()))
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
            return ToolCallResult.waitingApproval(approvalRequest.getId());
        }
        return handler.get().execute(request);
    }

    private java.util.Optional<AgentToolHandler> findHandler(String toolCode) {
        return handlers.stream()
                .filter(handler -> handler.toolCode().equals(toolCode))
                .findFirst();
    }
}
