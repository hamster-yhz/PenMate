package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.AgentTaskStateMachine;
import com.penmate.backend.application.agent.ToolInvocationGateway;
import com.penmate.backend.application.agent.ToolInvocationGatewayResult;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.ReviewApprovalCommand;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 审批应用服务。
 * <p>
 * 负责审批单创建、查询与审核通过/驳回，并向项目实时通道发布审批状态变更事件。
 * 在 agent tool 场景下，它同时负责根据审批结果驱动后续动作；
 * 真正的恢复执行仍需委派给 {@link ToolInvocationGateway} 做最终路由。
 * </p>
 */
@Service
@Slf4j
public class ApprovalApplicationService {

    /** 审批单仓储，负责审批记录的持久化与状态流转。 */
    private final ApprovalRequestRepository approvalRequestRepository;

    /** Agent 仓储，用于查询与回写生成任务状态。 */
    private final AgentRepository agentRepository;

    /** 任务状态机，用于校验 waiting_approval、running、failed 等状态流转是否合法。 */
    private final AgentTaskStateMachine taskStateMachine;

    /** 待恢复调用快照仓储，用于取回审批挂起时保存的 tool 调用现场。 */
    private final PendingToolInvocationRepository pendingToolInvocationRepository;

    /** Tool 调用统一网关，审批通过后由其继续恢复并路由到目标 handler。 */
    private final ToolInvocationGateway toolInvocationGateway;

    /** 审批通过后的异步恢复执行器。 */
    private final ApprovedToolInvocationAsyncResumer approvedToolInvocationAsyncResumer;

    /** 实时事件服务，用于广播审批与任务状态事件。 */
    private final RealtimeEventService realtimeEventService;

    public ApprovalApplicationService(ApprovalRequestRepository approvalRequestRepository,
                                      AgentRepository agentRepository,
                                       AgentTaskStateMachine taskStateMachine,
                                       PendingToolInvocationRepository pendingToolInvocationRepository,
                                       ToolInvocationGateway toolInvocationGateway,
                                       ApprovedToolInvocationAsyncResumer approvedToolInvocationAsyncResumer,
                                       RealtimeEventService realtimeEventService) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.agentRepository = agentRepository;
        this.taskStateMachine = taskStateMachine;
        this.pendingToolInvocationRepository = pendingToolInvocationRepository;
        this.toolInvocationGateway = toolInvocationGateway;
        this.approvedToolInvocationAsyncResumer = approvedToolInvocationAsyncResumer;
        this.realtimeEventService = realtimeEventService;
    }

    /**
     * 创建审批申请。
     *
     * @param command 审批创建命令
     * @param traceId 当前链路 traceId
     * @return 新创建的审批单
     */
    public ApprovalRequest create(CreateApprovalCommand command, String traceId) {
        log.info("创建审批申请: projectId={}, taskId={}, type={}, riskLevel={}, requestedBy={}",
                command.projectId(), command.taskId(), command.approvalType(), command.riskLevel(), command.requestedBy());
        ApprovalRequest request = new ApprovalRequest();
        request.setProjectId(command.projectId());
        request.setTaskId(command.taskId());
        request.setApprovalType(command.approvalType());
        request.setPayloadJson(command.payloadJson());
        request.setRiskLevel(command.riskLevel());
        request.setRequestedBy(command.requestedBy());
        int affected = approvalRequestRepository.insert(request);
        if (affected != 1) {
            log.error("创建审批申请失败: projectId={}, taskId={}, type={}", command.projectId(), command.taskId(), command.approvalType());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create approval request");
        }
        realtimeEventService.publishProjectEvent(command.projectId(), "approval.created", Map.of(
                "approvalId", request.getId(),
                "taskId", request.getTaskId(),
                "approvalType", request.getApprovalType(),
                "riskLevel", request.getRiskLevel(),
                "status", request.getStatus()
        ));
        writeAudit(traceId, command.requestedBy(), "approval", "create", "agent_approval_requests", String.valueOf(request.getId()), command.payloadJson(), 201);
        log.info("创建审批申请成功: approvalId={}, projectId={}, status={}", request.getId(), command.projectId(), request.getStatus());
        return request;
    }

    /**
     * 查询项目下审批申请列表。
     *
     * @param projectId 项目 ID
     * @return 项目下审批申请列表
     */
    public List<ApprovalRequest> listByProject(Long projectId) {
        List<ApprovalRequest> requests = approvalRequestRepository.findByProjectId(projectId);
        log.info("查询项目审批列表: projectId={}, count={}", projectId, requests.size());
        return requests;
    }

    /**
     * 查询审批申请详情。
     *
     * @param approvalId 审批单 ID
     * @return 审批单详情
     */
    public ApprovalRequest detail(Long approvalId) {
        log.info("查询审批详情: approvalId={}", approvalId);
        ApprovalRequest request = approvalRequestRepository.findById(approvalId);
        if (request == null) {
            log.warn("查询审批详情失败: approvalId={}, reason=not_found", approvalId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Approval request not found");
        }
        log.info("查询审批详情成功: approvalId={}, projectId={}, status={}", approvalId, request.getProjectId(), request.getStatus());
        return request;
    }

    /**
     * 审核通过审批申请。
     *
     * @param approvalId 审批单 ID
     * @param command 审核命令
     * @param traceId 当前链路 traceId
     */
    public void approve(Long approvalId, ReviewApprovalCommand command, String traceId) {
        log.info("审批通过请求: approvalId={}, reviewedBy={}", approvalId, command.reviewedBy());
        int affected = approvalRequestRepository.approve(approvalId, command.reviewedBy(), command.comment());
        if (affected != 1) {
            log.warn("审批通过失败: approvalId={}, reviewedBy={}, reason=invalid_status_or_not_found", approvalId, command.reviewedBy());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Approval is not in pending status or not found");
        }
        ApprovalRequest request = detail(approvalId);
        realtimeEventService.publishProjectEvent(request.getProjectId(), "approval.reviewed", Map.of(
                "approvalId", approvalId,
                "status", "approved",
                "reviewedBy", command.reviewedBy(),
                "comment", command.comment()
        ));
        resumeToolInvocationAfterApproved(request, traceId);
        writeAudit(traceId, command.reviewedBy(), "approval", "approve", "agent_approval_requests", String.valueOf(approvalId), command.comment(), 200);
        log.info("审批通过成功: approvalId={}, reviewedBy={}", approvalId, command.reviewedBy());
    }

    /**
     * 驳回审批申请。
     *
     * @param approvalId 审批单 ID
     * @param command 审核命令
     * @param traceId 当前链路 traceId
     */
    public void reject(Long approvalId, ReviewApprovalCommand command, String traceId) {
        log.info("审批驳回请求: approvalId={}, reviewedBy={}", approvalId, command.reviewedBy());
        int affected = approvalRequestRepository.reject(approvalId, command.reviewedBy(), command.comment());
        if (affected != 1) {
            log.warn("审批驳回失败: approvalId={}, reviewedBy={}, reason=invalid_status_or_not_found", approvalId, command.reviewedBy());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Approval is not in pending status or not found");
        }
        ApprovalRequest request = detail(approvalId);
        realtimeEventService.publishProjectEvent(request.getProjectId(), "approval.reviewed", Map.of(
                "approvalId", approvalId,
                "status", "rejected",
                "reviewedBy", command.reviewedBy(),
                "comment", command.comment()
        ));
        markTaskFailedAfterRejected(request);
        writeAudit(traceId, command.reviewedBy(), "approval", "reject", "agent_approval_requests", String.valueOf(approvalId), command.comment(), 200);
        log.info("审批驳回成功: approvalId={}, reviewedBy={}", approvalId, command.reviewedBy());
    }

    /**
     * 审批通过后恢复任务执行。
     * <p>
     * 当前实现先原子 claim 快照，再委派异步恢复执行器。
     * 这样可避免“任务先改为 {@code running}、快照却未成功 claim”的状态裂缝；
     * 同一审批重复回调时，也只会有一个线程成功把快照从 {@code pending} 推进到 {@code executing}。
     * </p>
     */
    private void resumeToolInvocationAfterApproved(ApprovalRequest request, String traceId) {
        if (request.getTaskId() == null || request.getProjectId() == null) {
            return;
        }
        PendingToolInvocationSnapshot snapshot = pendingToolInvocationRepository.findByApprovalId(request.getId());
        if (snapshot == null) {
            return;
        }
        // 先通过快照状态原子推进 claim 恢复执行权，避免任务状态先前移而快照未被成功抢占。
        int claimed = pendingToolInvocationRepository.markStatus(request.getId(), "pending", "executing");
        if (claimed != 1) {
            return;
        }
        approvedToolInvocationAsyncResumer.resumeApprovedInvocation(request, snapshot);
    }

    /**
     * 审批驳回后终止任务。
     * <p>仅当任务仍处于 waiting_approval 时回写 failed 并广播失败事件。</p>
     */
    private void markTaskFailedAfterRejected(ApprovalRequest request) {
        if (request.getTaskId() == null || request.getProjectId() == null) {
            return;
        }
        AgentGenerationTask task = agentRepository.findGenerationTask(request.getProjectId(), request.getTaskId());
        if (task == null) {
            return;
        }
        AgentTaskStatus currentStatus = taskStateMachine.parseStatus(task.getStatus());
        if (currentStatus != AgentTaskStatus.WAITING_APPROVAL) {
            return;
        }
        // 驳回语义不是“延后执行”，而是“当前高风险操作被明确拒绝”，因此直接进入 failed。
        taskStateMachine.assertTransition(currentStatus.value(), AgentTaskStatus.FAILED);
        agentRepository.updateGenerationTaskStatus(request.getProjectId(), request.getTaskId(), AgentTaskStatus.FAILED.value(), "Approval rejected");
        realtimeEventService.publishGenerationFailed(request.getProjectId(), request.getTaskId(), "AGENT_APPROVAL_REQUIRED", "Approval rejected");
    }

    /**
     * 审计写入占位方法。
     *
     * @param traceId 当前链路 traceId
     * @param userId 操作者 ID
     * @param module 模块名
     * @param action 动作名
     * @param resourceType 资源类型
     * @param resourceId 资源 ID
     * @param requestJson 请求摘要
     * @param responseCode 响应码
     */
    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        // 审计模块已移除
    }
}


