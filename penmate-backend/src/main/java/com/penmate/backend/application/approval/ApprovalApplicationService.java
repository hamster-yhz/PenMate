package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.AgentOrchestrationDispatcher;
import com.penmate.backend.application.agent.AgentTaskStateMachine;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.ReviewApprovalCommand;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 审批应用服务。
 * <p>负责审批单创建、查询与审核通过/驳回，并向项目实时通道发布审批状态变更事件。</p>
 */
@Service
@Slf4j
public class ApprovalApplicationService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final AgentRepository agentRepository;
    private final AgentTaskStateMachine taskStateMachine;
    private final AgentOrchestrationDispatcher orchestrationDispatcher;
    private final RealtimeEventService realtimeEventService;

    public ApprovalApplicationService(ApprovalRequestRepository approvalRequestRepository,
                                      AgentRepository agentRepository,
                                      AgentTaskStateMachine taskStateMachine,
                                      AgentOrchestrationDispatcher orchestrationDispatcher,
                                      RealtimeEventService realtimeEventService) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.agentRepository = agentRepository;
        this.taskStateMachine = taskStateMachine;
        this.orchestrationDispatcher = orchestrationDispatcher;
        this.realtimeEventService = realtimeEventService;
    }

    /**
     * 创建审批申请。
     *
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
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
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<ApprovalRequest> listByProject(Long projectId) {
        List<ApprovalRequest> requests = approvalRequestRepository.findByProjectId(projectId);
        log.info("查询项目审批列表: projectId={}, count={}", projectId, requests.size());
        return requests;
    }

    /**
     * 查询审批申请详情。
     *
     * @param approvalId 入参：approvalId
     * @return 出参：处理结果
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
     * @param approvalId 入参：approvalId
     * @param command 入参：command
     * @param traceId 入参：traceId
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
        resumeTaskAfterApproved(request, traceId);
        writeAudit(traceId, command.reviewedBy(), "approval", "approve", "agent_approval_requests", String.valueOf(approvalId), command.comment(), 200);
        log.info("审批通过成功: approvalId={}, reviewedBy={}", approvalId, command.reviewedBy());
    }

    /**
     * 驳回审批申请。
     *
     * @param approvalId 入参：approvalId
     * @param command 入参：command
     * @param traceId 入参：traceId
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
     * <p>只在 waiting_approval 状态下先回写 running，再异步继续编排主链路。</p>
     */
    private void resumeTaskAfterApproved(ApprovalRequest request, String traceId) {
        if (request.getTaskId() == null || request.getProjectId() == null) {
            return;
        }
        AgentGenerationTask task = agentRepository.findGenerationTask(request.getProjectId(), request.getTaskId());
        if (task == null) {
            return;
        }
        AgentTaskStatus currentStatus = taskStateMachine.parseStatus(task.getStatus());
        if (currentStatus == AgentTaskStatus.WAITING_APPROVAL) {
            // 先恢复为 running，再交由编排器继续执行，保证状态机前后连贯。
            taskStateMachine.assertTransition(currentStatus.value(), AgentTaskStatus.RUNNING);
            agentRepository.updateGenerationTaskStatus(request.getProjectId(), request.getTaskId(), AgentTaskStatus.RUNNING.value(), null);
        }
        // 审批恢复场景跳过审批门禁，避免二次进入 waiting_approval。
        orchestrationDispatcher.dispatchAfterApproval(request.getProjectId(), request.getTaskId(), traceId);
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
        taskStateMachine.assertTransition(currentStatus.value(), AgentTaskStatus.FAILED);
        agentRepository.updateGenerationTaskStatus(request.getProjectId(), request.getTaskId(), AgentTaskStatus.FAILED.value(), "Approval rejected");
        realtimeEventService.publishGenerationFailed(request.getProjectId(), request.getTaskId(), "AGENT_APPROVAL_REQUIRED", "Approval rejected");
    }

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


