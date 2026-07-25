package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.run.AgentRunEventPublisher;
import com.penmate.backend.application.agent.run.AgentRunResumeDispatcher;
import com.penmate.backend.application.agent.run.AgentRunLeaseService;
import com.penmate.backend.application.agent.security.AgentResourceAccessGuard;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.ReviewApprovalCommand;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ApprovalApplicationService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final AgentRunPendingApprovalRepository pendingApprovalRepository;
    private final AgentRunEventPublisher eventPublisher;
    private final AgentRunResumeDispatcher runResumeDispatcher;
    private final AgentRunLeaseService runLeaseService;
    private final BusinessIdGenerator businessIdGenerator;
    private final AgentResourceAccessGuard accessGuard;

    public ApprovalApplicationService(ApprovalRequestRepository approvalRequestRepository,
                                      AgentRunPendingApprovalRepository pendingApprovalRepository,
                                      AgentRunEventPublisher eventPublisher,
                                      AgentRunResumeDispatcher runResumeDispatcher,
                                      AgentRunLeaseService runLeaseService,
                                      BusinessIdGenerator businessIdGenerator,
                                      AgentResourceAccessGuard accessGuard) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.pendingApprovalRepository = pendingApprovalRepository;
        this.eventPublisher = eventPublisher;
        this.runResumeDispatcher = runResumeDispatcher;
        this.runLeaseService = runLeaseService;
        this.businessIdGenerator = businessIdGenerator;
        this.accessGuard = accessGuard;
    }

    public ApprovalRequest create(CreateApprovalCommand command, String traceId) {
        accessGuard.requireProject(command.projectId(), command.requestedBy());
        if (command.runId() != null) {
            accessGuard.requireRun(command.projectId(), command.runId(), command.requestedBy());
        }
        log.info("创建审批申请: projectId={}, runId={}, type={}, riskLevel={}, requestedBy={}",
                command.projectId(), command.runId(), command.approvalType(), command.riskLevel(), command.requestedBy());
        ApprovalRequest request = new ApprovalRequest();
        request.setApprovalRequestId(businessIdGenerator.nextId());
        request.setProjectId(command.projectId());
        request.setRunId(command.runId());
        request.setApprovalType(command.approvalType());
        request.setPayloadJson(command.payloadJson());
        request.setRiskLevel(command.riskLevel());
        request.setRequestedBy(command.requestedBy());
        int affected = approvalRequestRepository.insert(request);
        if (affected != 1) {
            log.error("创建审批申请失败: projectId={}, runId={}, type={}", command.projectId(), command.runId(), command.approvalType());
            throw BusinessException.of("Failed to create approval request");
        }
        if (request.getRunId() != null) {
            eventPublisher.publish(request.getRunId(), "approval.requested", Map.of(
                    "approvalId", request.getApprovalRequestId(),
                    "approvalType", request.getApprovalType(),
                    "riskLevel", request.getRiskLevel(),
                    "status", request.getStatus() == null ? "pending" : request.getStatus(),
                    "traceId", traceId
            ));
        }
        writeAudit(traceId, command.requestedBy(), "approval", "create", "agent_approval_requests",
                String.valueOf(request.getApprovalRequestId()), command.payloadJson(), 201);
        log.info("创建审批申请成功: approvalId={}, projectId={}, runId={}, status={}",
                request.getApprovalRequestId(), command.projectId(), command.runId(), request.getStatus());
        return request;
    }

    public List<ApprovalRequest> listByProject(Long projectId, Long actorUserId) {
        accessGuard.requireProject(projectId, actorUserId);
        List<ApprovalRequest> requests = approvalRequestRepository.findByProjectId(projectId);
        log.info("查询项目审批列表: projectId={}, count={}", projectId, requests.size());
        return requests;
    }

    public ApprovalRequest detail(Long projectId, Long approvalId, Long actorUserId) {
        log.info("查询审批详情: approvalId={}", approvalId);
        ApprovalRequest request = accessGuard.requireApproval(projectId, approvalId, actorUserId);
        if (request == null) {
            log.warn("查询审批详情失败: approvalId={}, reason=not_found", approvalId);
            throw BusinessException.of("Approval request not found");
        }
        log.info("查询审批详情成功: approvalId={}, projectId={}, runId={}, status={}",
                approvalId, request.getProjectId(), request.getRunId(), request.getStatus());
        return request;
    }

    public void approve(Long projectId, Long approvalId, ReviewApprovalCommand command, String traceId) {
        log.info("审批通过请求: approvalId={}, reviewedBy={}", approvalId, command.reviewedBy());
        ApprovalRequest request = accessGuard.requireApproval(projectId, approvalId, command.reviewedBy());
        int affected = approvalRequestRepository.approveByApprovalRequestId(
                projectId, approvalId, command.reviewedBy(), command.comment());
        if (affected != 1) {
            if ("approved".equalsIgnoreCase(request.getStatus())) {
                log.info("审批通过重复回调跳过: approvalId={}, status={}", approvalId, request.getStatus());
                return;
            }
            log.warn("审批通过失败: approvalId={}, reviewedBy={}, reason=invalid_status_or_not_found", approvalId, command.reviewedBy());
            throw BusinessException.of("Approval is not in pending status or not found");
        }
        AgentRunPendingApproval pendingApproval = pendingApprovalRepository.findByApprovalId(approvalId);
        if (pendingApproval == null) {
            log.warn("审批通过后未找到 run pending approval: approvalId={}", approvalId);
            return;
        }
        int marked = pendingApprovalRepository.markStatus(approvalId, "PENDING", "APPROVED");
        if (marked != 1) {
            log.info("审批通过重复 pending 状态迁移跳过: approvalId={}", approvalId);
            return;
        }
        eventPublisher.publish(pendingApproval.runId(), "approval.approved", Map.of(
                "approvalId", approvalId,
                "reviewedBy", command.reviewedBy(),
                "comment", command.comment() == null ? "" : command.comment(),
                "traceId", traceId
        ));
        runResumeDispatcher.dispatchResume(pendingApproval.runId(), traceId);
        writeAudit(traceId, command.reviewedBy(), "approval", "approve", "agent_approval_requests", String.valueOf(approvalId), command.comment(), 200);
        log.info("审批通过成功: approvalId={}, runId={}, reviewedBy={}", approvalId, pendingApproval.runId(), command.reviewedBy());
    }

    public void reject(Long projectId, Long approvalId, ReviewApprovalCommand command, String traceId) {
        log.info("审批驳回请求: approvalId={}, reviewedBy={}", approvalId, command.reviewedBy());
        ApprovalRequest request = accessGuard.requireApproval(projectId, approvalId, command.reviewedBy());
        int affected = approvalRequestRepository.rejectByApprovalRequestId(
                projectId, approvalId, command.reviewedBy(), command.comment());
        if (affected != 1) {
            if ("rejected".equalsIgnoreCase(request.getStatus())) {
                log.info("审批驳回重复回调跳过: approvalId={}, status={}", approvalId, request.getStatus());
                return;
            }
            log.warn("审批驳回失败: approvalId={}, reviewedBy={}, reason=invalid_status_or_not_found", approvalId, command.reviewedBy());
            throw BusinessException.of("Approval is not in pending status or not found");
        }
        AgentRunPendingApproval pendingApproval = pendingApprovalRepository.findByApprovalId(approvalId);
        if (pendingApproval == null) {
            log.warn("审批驳回后未找到 run pending approval: approvalId={}", approvalId);
            return;
        }
        int marked = pendingApprovalRepository.markStatus(approvalId, "PENDING", "REJECTED");
        if (marked != 1) {
            log.info("审批驳回重复 pending 状态迁移跳过: approvalId={}", approvalId);
            return;
        }
        runLeaseService.cancelWaitingApproval(pendingApproval.runId(),
                "AGENT_APPROVAL_REJECTED", "Approval rejected");
        eventPublisher.publish(pendingApproval.runId(), "approval.rejected", Map.of(
                "approvalId", approvalId,
                "reviewedBy", command.reviewedBy(),
                "comment", command.comment() == null ? "" : command.comment(),
                "traceId", traceId
        ));
        eventPublisher.publish(pendingApproval.runId(), "run.cancelled", Map.of(
                "errorCode", "AGENT_APPROVAL_REJECTED",
                "errorMessage", "Approval rejected",
                "approvalId", approvalId
        ));
        writeAudit(traceId, command.reviewedBy(), "approval", "reject", "agent_approval_requests", String.valueOf(approvalId), command.comment(), 200);
        log.info("审批驳回成功: approvalId={}, runId={}, reviewedBy={}", approvalId, pendingApproval.runId(), command.reviewedBy());
    }

    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        // Audit module removed.
    }
}
