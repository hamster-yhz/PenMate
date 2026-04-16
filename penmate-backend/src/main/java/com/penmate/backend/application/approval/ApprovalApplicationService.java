package com.penmate.backend.application.approval;

import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.ReviewApprovalCommand;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import com.penmate.backend.domain.shared.service.AuditService;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 审批应用服务。
 * <p>负责审批单创建、查询与审核通过/驳回，并向项目实时通道发布审批状态变更事件。</p>
 */
@Service
@Slf4j
public class ApprovalApplicationService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final AuditService auditService;
    private final RealtimeEventService realtimeEventService;

    public ApprovalApplicationService(ApprovalRequestRepository approvalRequestRepository,
                                      AuditService auditService,
                                      RealtimeEventService realtimeEventService) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.auditService = auditService;
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
        realtimeEventService.publishProjectEvent(detail(approvalId).getProjectId(), "approval.reviewed", Map.of(
                "approvalId", approvalId,
                "status", "approved",
                "reviewedBy", command.reviewedBy(),
                "comment", command.comment()
        ));
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
        realtimeEventService.publishProjectEvent(detail(approvalId).getProjectId(), "approval.reviewed", Map.of(
                "approvalId", approvalId,
                "status", "rejected",
                "reviewedBy", command.reviewedBy(),
                "comment", command.comment()
        ));
        writeAudit(traceId, command.reviewedBy(), "approval", "reject", "agent_approval_requests", String.valueOf(approvalId), command.comment(), 200);
        log.info("审批驳回成功: approvalId={}, reviewedBy={}", approvalId, command.reviewedBy());
    }

    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        String finalTraceId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        auditService.write(finalTraceId, userId, module, action, resourceType, resourceId, requestJson, responseCode);
    }
}


