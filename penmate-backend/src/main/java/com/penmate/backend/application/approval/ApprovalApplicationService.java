package com.penmate.backend.application.approval;

import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.infrastructure.persistence.approval.ApprovalRequestMapper;
import com.penmate.backend.infrastructure.persistence.audit.AuditLogMapper;
import com.penmate.backend.interfaces.api.approval.dto.CreateApprovalRequestDto;
import com.penmate.backend.interfaces.api.approval.dto.ReviewApprovalRequestDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ApprovalApplicationService {

    private final ApprovalRequestMapper approvalRequestMapper;
    private final AuditLogMapper auditLogMapper;

    public ApprovalApplicationService(ApprovalRequestMapper approvalRequestMapper,
                                      AuditLogMapper auditLogMapper) {
        this.approvalRequestMapper = approvalRequestMapper;
        this.auditLogMapper = auditLogMapper;
    }

    public ApprovalRequest create(CreateApprovalRequestDto dto, String traceId) {
        ApprovalRequest request = new ApprovalRequest();
        request.setProjectId(dto.getProjectId());
        request.setTaskId(dto.getTaskId());
        request.setApprovalType(dto.getApprovalType());
        request.setPayloadJson(dto.getPayloadJson());
        request.setRiskLevel(dto.getRiskLevel());
        request.setRequestedBy(dto.getRequestedBy());
        int affected = approvalRequestMapper.insert(request);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to create approval request");
        }
        writeAudit(traceId, dto.getRequestedBy(), "approval", "create", "agent_approval_requests", String.valueOf(request.getId()), dto.getPayloadJson(), 201);
        return request;
    }

    public List<ApprovalRequest> listByProject(Long projectId) {
        return approvalRequestMapper.findByProjectId(projectId);
    }

    public ApprovalRequest detail(Long approvalId) {
        ApprovalRequest request = approvalRequestMapper.findById(approvalId);
        if (request == null) {
            throw new IllegalArgumentException("Approval request not found");
        }
        return request;
    }

    public void approve(Long approvalId, ReviewApprovalRequestDto dto, String traceId) {
        int affected = approvalRequestMapper.approve(approvalId, dto.getReviewedBy(), dto.getComment());
        if (affected != 1) {
            throw new IllegalArgumentException("Approval is not in pending status or not found");
        }
        writeAudit(traceId, dto.getReviewedBy(), "approval", "approve", "agent_approval_requests", String.valueOf(approvalId), dto.getComment(), 200);
    }

    public void reject(Long approvalId, ReviewApprovalRequestDto dto, String traceId) {
        int affected = approvalRequestMapper.reject(approvalId, dto.getReviewedBy(), dto.getComment());
        if (affected != 1) {
            throw new IllegalArgumentException("Approval is not in pending status or not found");
        }
        writeAudit(traceId, dto.getReviewedBy(), "approval", "reject", "agent_approval_requests", String.valueOf(approvalId), dto.getComment(), 200);
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
        auditLogMapper.insert(finalTraceId, userId, module, action, resourceType, resourceId, requestJson, responseCode);
    }
}

