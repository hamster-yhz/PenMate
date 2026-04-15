package com.penmate.backend.application.approval;

import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.ReviewApprovalCommand;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import com.penmate.backend.domain.shared.service.AuditService;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ApprovalApplicationService。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
@Service
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
     * 创建业务数据。
     *
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public ApprovalRequest create(CreateApprovalCommand command, String traceId) {
        ApprovalRequest request = new ApprovalRequest();
        request.setProjectId(command.projectId());
        request.setTaskId(command.taskId());
        request.setApprovalType(command.approvalType());
        request.setPayloadJson(command.payloadJson());
        request.setRiskLevel(command.riskLevel());
        request.setRequestedBy(command.requestedBy());
        int affected = approvalRequestRepository.insert(request);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to create approval request");
        }
        realtimeEventService.publishProjectEvent(command.projectId(), "approval.created", Map.of(
                "approvalId", request.getId(),
                "taskId", request.getTaskId(),
                "approvalType", request.getApprovalType(),
                "riskLevel", request.getRiskLevel(),
                "status", request.getStatus()
        ));
        writeAudit(traceId, command.requestedBy(), "approval", "create", "agent_approval_requests", String.valueOf(request.getId()), command.payloadJson(), 201);
        return request;
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<ApprovalRequest> listByProject(Long projectId) {
        return approvalRequestRepository.findByProjectId(projectId);
    }

    /**
     * 处理业务请求。
     *
     * @param approvalId 入参：approvalId
     * @return 出参：处理结果
     */
    public ApprovalRequest detail(Long approvalId) {
        ApprovalRequest request = approvalRequestRepository.findById(approvalId);
        if (request == null) {
            throw new IllegalArgumentException("Approval request not found");
        }
        return request;
    }

    /**
     * 处理业务请求。
     *
     * @param approvalId 入参：approvalId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void approve(Long approvalId, ReviewApprovalCommand command, String traceId) {
        int affected = approvalRequestRepository.approve(approvalId, command.reviewedBy(), command.comment());
        if (affected != 1) {
            throw new IllegalArgumentException("Approval is not in pending status or not found");
        }
        realtimeEventService.publishProjectEvent(detail(approvalId).getProjectId(), "approval.reviewed", Map.of(
                "approvalId", approvalId,
                "status", "approved",
                "reviewedBy", command.reviewedBy(),
                "comment", command.comment()
        ));
        writeAudit(traceId, command.reviewedBy(), "approval", "approve", "agent_approval_requests", String.valueOf(approvalId), command.comment(), 200);
    }

    /**
     * 处理业务请求。
     *
     * @param approvalId 入参：approvalId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void reject(Long approvalId, ReviewApprovalCommand command, String traceId) {
        int affected = approvalRequestRepository.reject(approvalId, command.reviewedBy(), command.comment());
        if (affected != 1) {
            throw new IllegalArgumentException("Approval is not in pending status or not found");
        }
        realtimeEventService.publishProjectEvent(detail(approvalId).getProjectId(), "approval.reviewed", Map.of(
                "approvalId", approvalId,
                "status", "rejected",
                "reviewedBy", command.reviewedBy(),
                "comment", command.comment()
        ));
        writeAudit(traceId, command.reviewedBy(), "approval", "reject", "agent_approval_requests", String.valueOf(approvalId), command.comment(), 200);
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

