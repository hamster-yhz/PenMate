package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.approval.coordination.AgentApprovalResumeCoordinator;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 审批通过后的异步恢复执行器。
 * <p>
 * 该组件只处理已经成功 claim 为 {@code executing} 的快照，
 * 因此不会和审批入口形成重复执行竞争；真正的并发互斥仍由快照状态原子推进保证。
 * </p>
 */
@Component
@Slf4j
public class ApprovedToolInvocationAsyncResumer {

    private final AgentRepository agentRepository;
    private final com.penmate.backend.domain.agent.service.AgentTaskTransitionPolicy taskStateMachine;
    private final PendingToolInvocationRepository pendingToolInvocationRepository;
    private final ApprovalAgentResumeCoordinator approvalAgentResumeCoordinator;
    private final RealtimeEventService realtimeEventService;

    public ApprovedToolInvocationAsyncResumer(AgentRepository agentRepository,
                                              com.penmate.backend.domain.agent.service.AgentTaskTransitionPolicy taskStateMachine,
                                              PendingToolInvocationRepository pendingToolInvocationRepository,
                                              ApprovalAgentResumeCoordinator approvalAgentResumeCoordinator,
                                              RealtimeEventService realtimeEventService) {
        this.agentRepository = agentRepository;
        this.taskStateMachine = taskStateMachine;
        this.pendingToolInvocationRepository = pendingToolInvocationRepository;
        this.approvalAgentResumeCoordinator = approvalAgentResumeCoordinator;
        this.realtimeEventService = realtimeEventService;
    }

    @Async
    public void resumeApprovedInvocation(ApprovalRequest request, PendingToolInvocationSnapshot snapshot) {
        try {
            if (!isSnapshotStillExecuting(request.getId())) {
                log.warn("审批通过后的异步恢复跳过: approvalId={}, reason=snapshot_not_executing, traceId={}",
                        request.getId(), snapshot.traceId());
                return;
            }
            markTaskRunningIfNeeded(request);
            ToolCallResult result = approvalAgentResumeCoordinator.resumeApprovedInvocation(request, snapshot);
            if ("FAILED".equals(result.status())) {
                sealSnapshotAndTaskAsFailed(request, snapshot, result.errorCode(), result.errorMessage());
                return;
            }
            int completed = pendingToolInvocationRepository.markStatus(request.getId(), "executing", "completed");
            if (completed != 1) {
                log.warn("审批通过后的异步恢复完成但快照未成功封口: approvalId={}, traceId={}",
                        request.getId(), snapshot.traceId());
            }
        } catch (Exception ex) {
            log.error("审批通过后的异步恢复执行异常: approvalId={}, taskId={}, traceId={}",
                    request.getId(), request.getTaskId(), snapshot.traceId(), ex);
            sealSnapshotAndTaskAsFailed(request, snapshot, "AGENT_APPROVAL_RESUME_FAILED", ex.getMessage());
        }
    }

    private boolean isSnapshotStillExecuting(Long approvalId) {
        PendingToolInvocationSnapshot currentSnapshot = pendingToolInvocationRepository.findByApprovalId(approvalId);
        return currentSnapshot != null && "executing".equals(currentSnapshot.status());
    }

    private void markTaskRunningIfNeeded(ApprovalRequest request) {
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
        taskStateMachine.assertTransition(currentStatus.value(), AgentTaskStatus.RUNNING);
        agentRepository.updateGenerationTaskStatus(request.getProjectId(), request.getTaskId(), AgentTaskStatus.RUNNING.value(), null);
        log.info("审批恢复桥接已切换任务状态: approvalId={}, taskId={}, from=WAITING_APPROVAL, to=RUNNING",
                request.getId(), request.getTaskId());
    }

    private void sealSnapshotAndTaskAsFailed(ApprovalRequest request,
                                             PendingToolInvocationSnapshot snapshot,
                                             String errorCode,
                                             String errorMessage) {
        pendingToolInvocationRepository.markStatus(request.getId(), "executing", "failed");
        if (request.getTaskId() == null || request.getProjectId() == null) {
            return;
        }
        AgentGenerationTask task = agentRepository.findGenerationTask(request.getProjectId(), request.getTaskId());
        if (task == null) {
            return;
        }
        AgentTaskStatus currentStatus = taskStateMachine.parseStatus(task.getStatus());
        if (currentStatus == AgentTaskStatus.FAILED
                || currentStatus == AgentTaskStatus.DONE
                || currentStatus == AgentTaskStatus.APPLIED
                || currentStatus == AgentTaskStatus.CANCELLED) {
            return;
        }
        taskStateMachine.assertTransition(currentStatus.value(), AgentTaskStatus.FAILED);
        agentRepository.updateGenerationTaskStatus(request.getProjectId(), request.getTaskId(), AgentTaskStatus.FAILED.value(), errorMessage);
        realtimeEventService.publishGenerationFailed(
                request.getProjectId(),
                request.getTaskId(),
                errorCode == null ? "AGENT_APPROVAL_RESUME_FAILED" : errorCode,
                errorMessage
        );
    }
}
