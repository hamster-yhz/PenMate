package com.penmate.backend.application.approval;

import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.ReviewApprovalCommand;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import com.penmate.backend.domain.shared.service.AuditService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ApprovalApplicationService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final AuditService auditService;

    public ApprovalApplicationService(ApprovalRequestRepository approvalRequestRepository,
                                      AuditService auditService) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.auditService = auditService;
    }

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
        writeAudit(traceId, command.requestedBy(), "approval", "create", "agent_approval_requests", String.valueOf(request.getId()), command.payloadJson(), 201);
        return request;
    }

    public List<ApprovalRequest> listByProject(Long projectId) {
        return approvalRequestRepository.findByProjectId(projectId);
    }

    public ApprovalRequest detail(Long approvalId) {
        ApprovalRequest request = approvalRequestRepository.findById(approvalId);
        if (request == null) {
            throw new IllegalArgumentException("Approval request not found");
        }
        return request;
    }

    public void approve(Long approvalId, ReviewApprovalCommand command, String traceId) {
        int affected = approvalRequestRepository.approve(approvalId, command.reviewedBy(), command.comment());
        if (affected != 1) {
            throw new IllegalArgumentException("Approval is not in pending status or not found");
        }
        writeAudit(traceId, command.reviewedBy(), "approval", "approve", "agent_approval_requests", String.valueOf(approvalId), command.comment(), 200);
    }

    public void reject(Long approvalId, ReviewApprovalCommand command, String traceId) {
        int affected = approvalRequestRepository.reject(approvalId, command.reviewedBy(), command.comment());
        if (affected != 1) {
            throw new IllegalArgumentException("Approval is not in pending status or not found");
        }
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

