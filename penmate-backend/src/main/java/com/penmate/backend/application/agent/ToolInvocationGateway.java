package com.penmate.backend.application.agent;

import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import com.penmate.backend.application.approval.DefaultApprovalPolicyEngine;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool 调用统一网关。
 * <p>
 * 该组件位于 agent 编排层与具体 tool handler 之间，统一承接一次工具调用，
 * 负责元数据读取、审批决策、快照挂起、任务状态切换，以及在审批通过后提供恢复入口并把调用路由到目标 handler。
 * </p>
 * <p>
 * 借助该网关，业务 handler 可以只关心“如何验证与执行工具业务”，
 * 而不必自行实现审批状态机逻辑。
 * </p>
 */
@Component
@Slf4j
public class ToolInvocationGateway {

    /** Tool 静态元数据注册表，用于按 toolCode 解析审批声明与风险信息。 */
    private final StaticToolMetadataRegistry toolMetadataRegistry;

    /** 审批策略引擎，结合元数据与本次请求参数计算最终审批决策。 */
    private final DefaultApprovalPolicyEngine approvalPolicyEngine;

    /** 审批应用服务，负责创建审批单并维护审批领域状态。 */
    private final ApprovalApplicationService approvalApplicationService;

    /** 待恢复调用快照仓储，用于保存因审批而挂起的原始 tool 调用现场。 */
    private final PendingToolInvocationRepository pendingToolInvocationRepository;

    /** Agent 仓储，用于回写生成任务状态。 */
    private final AgentRepository agentRepository;

    /** 实时事件服务，用于广播等待审批等任务事件。 */
    private final RealtimeEventService realtimeEventService;

    /** 已注册的 tool handler 列表，按 toolCode 路由到具体执行器。 */
    private final List<AgentToolHandler> handlers;

    public ToolInvocationGateway(StaticToolMetadataRegistry toolMetadataRegistry,
                                 DefaultApprovalPolicyEngine approvalPolicyEngine,
                                 ApprovalApplicationService approvalApplicationService,
                                 PendingToolInvocationRepository pendingToolInvocationRepository,
                                 AgentRepository agentRepository,
                                 RealtimeEventService realtimeEventService,
                                 List<AgentToolHandler> handlers) {
        this.toolMetadataRegistry = toolMetadataRegistry;
        this.approvalPolicyEngine = approvalPolicyEngine;
        this.approvalApplicationService = approvalApplicationService;
        this.pendingToolInvocationRepository = pendingToolInvocationRepository;
        this.agentRepository = agentRepository;
        this.realtimeEventService = realtimeEventService;
        this.handlers = handlers;
    }

    /**
     * 发起一次 tool 调用。
     * <p>
     * 该入口先完成 handler 定位与参数校验，再根据审批策略决定：
     * 直接执行，或先挂起为待审批快照。
     * </p>
     *
     * @param request 本次 tool 调用请求
     * @return 统一结果，可能为成功、失败或等待审批
     */
    public ToolInvocationGatewayResult invoke(ToolInvocationRequest request) {
        log.info("发起 tool 调用: toolCode={}, projectId={}, taskId={}, conversationId={}, traceId={}",
                request.toolCode(), request.projectId(), request.taskId(), request.conversationId(), request.traceId());
        ToolMetadata metadata = toolMetadataRegistry.getRequired(request.toolCode());
        java.util.Optional<AgentToolHandler> handler = findHandler(request.toolCode());
        if (handler.isEmpty()) {
            log.warn("tool 调用失败: toolCode={}, reason=handler_not_found, traceId={}", request.toolCode(), request.traceId());
            return new ToolInvocationGatewayResult(
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
            return new ToolInvocationGatewayResult(
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
            log.info("tool 调用进入审批挂起: toolCode={}, approvalType={}, approvalId={}, taskId={}, traceId={}",
                    request.toolCode(), decision.approvalType(), approvalRequest.getId(), request.taskId(), request.traceId());
            // 审批命中后保存一份可恢复的调用现场，确保人工通过后可以继续执行“同一次” tool 调用。
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
                    "pending"
            ));
            // 将任务切到等待审批中间态，使前端、编排器和后续恢复逻辑都能识别当前阻塞点。
            agentRepository.updateGenerationTaskStatus(request.projectId(), request.taskId(), "waiting_approval", null);
            realtimeEventService.publishGenerationWaitingApproval(
                    request.projectId(),
                    request.taskId(),
                    approvalRequest.getId(),
                    decision.approvalType()
            );
            return ToolInvocationGatewayResult.waitingApproval(approvalRequest.getId());
        }
        ToolInvocationGatewayResult result = handler.get().execute(request);
        log.info("tool 调用执行完成: toolCode={}, status={}, traceId={}", request.toolCode(), result.status(), request.traceId());
        return result;
    }

    /**
     * 恢复一条已审批通过的挂起 tool 调用。
     * <p>
     * 恢复阶段不会再次走审批策略，而是直接把快照重新装配为
     * {@link ToolInvocationRequest} 并路由到对应 handler 执行。
     * </p>
     *
     * @param snapshot 审批挂起阶段保存的调用快照
     * @return 恢复执行结果
     */
    public ToolInvocationGatewayResult resume(PendingToolInvocationSnapshot snapshot) {
        log.info("恢复挂起 tool 调用: toolCode={}, approvalId={}, taskId={}, traceId={}",
                snapshot.toolCode(), snapshot.approvalId(), snapshot.taskId(), snapshot.traceId());
        // 恢复调用时沿用原始 traceId、contextJson 与 idempotencyKey，
        // 以保证审批前后日志、上下文与幂等语义保持一致。
        ToolInvocationRequest request = new ToolInvocationRequest(
                snapshot.projectId(),
                snapshot.taskId(),
                snapshot.conversationId(),
                snapshot.toolCode(),
                snapshot.toolArgsJson(),
                snapshot.operatorId(),
                snapshot.traceId(),
                snapshot.contextJson(),
                snapshot.idempotencyKey()
        );
        return findHandler(snapshot.toolCode())
                .map(handler -> {
                    ToolInvocationGatewayResult result = handler.execute(request);
                    log.info("恢复挂起 tool 调用完成: toolCode={}, approvalId={}, status={}, traceId={}",
                            snapshot.toolCode(), snapshot.approvalId(), result.status(), snapshot.traceId());
                    return result;
                })
                .orElseGet(() -> {
                    log.warn("恢复挂起 tool 调用失败: toolCode={}, approvalId={}, reason=handler_not_found, traceId={}",
                            snapshot.toolCode(), snapshot.approvalId(), snapshot.traceId());
                    return new ToolInvocationGatewayResult(
                            "FAILED",
                            null,
                            null,
                            "TOOL_HANDLER_NOT_FOUND",
                            "Tool handler not found: " + snapshot.toolCode()
                    );
                });
    }

    /**
     * 根据 toolCode 查找实际 handler。
     *
     * @param toolCode tool 唯一编码
     * @return 命中的 handler；找不到时返回 empty
     */
    private java.util.Optional<AgentToolHandler> findHandler(String toolCode) {
        return handlers.stream()
                .filter(handler -> handler.toolCode().equals(toolCode))
                .findFirst();
    }
}
