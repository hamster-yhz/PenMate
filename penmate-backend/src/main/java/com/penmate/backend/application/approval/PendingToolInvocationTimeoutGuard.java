package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.AgentTaskStateMachine;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时 executing 快照轻量治理。
 */
@Component
@Slf4j
public class PendingToolInvocationTimeoutGuard {

    static final int DEFAULT_TIMEOUT_MINUTES = 10;
    static final int DEFAULT_BATCH_SIZE = 100;
    static final String TIMEOUT_ERROR_MESSAGE = "Pending tool invocation resume timed out";
    static final String TIMEOUT_ERROR_CODE = "AGENT_APPROVAL_RESUME_TIMEOUT";

    private final PendingToolInvocationRepository pendingToolInvocationRepository;
    private final AgentRepository agentRepository;
    private final AgentTaskStateMachine taskStateMachine;
    private final RealtimeEventService realtimeEventService;

    public PendingToolInvocationTimeoutGuard(PendingToolInvocationRepository pendingToolInvocationRepository,
                                             AgentRepository agentRepository,
                                             AgentTaskStateMachine taskStateMachine,
                                             RealtimeEventService realtimeEventService) {
        this.pendingToolInvocationRepository = pendingToolInvocationRepository;
        this.agentRepository = agentRepository;
        this.taskStateMachine = taskStateMachine;
        this.realtimeEventService = realtimeEventService;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void failTimedOutExecutingSnapshots() {
        List<PendingToolInvocationSnapshot> snapshots = pendingToolInvocationRepository
                .findStaleExecutingSnapshots(DEFAULT_TIMEOUT_MINUTES, DEFAULT_BATCH_SIZE);
        for (PendingToolInvocationSnapshot snapshot : snapshots) {
            if (!shouldFailStuckHandoff(snapshot)) {
                continue;
            }
            int sealed = pendingToolInvocationRepository.markStatus(snapshot.approvalId(), "executing", "failed");
            if (sealed != 1) {
                continue;
            }
            failWaitingApprovalTask(snapshot);
        }
    }

    private boolean shouldFailStuckHandoff(PendingToolInvocationSnapshot snapshot) {
        if (snapshot.projectId() == null || snapshot.taskId() == null) {
            return false;
        }
        AgentGenerationTask task = agentRepository.findGenerationTask(snapshot.projectId(), snapshot.taskId());
        if (task == null) {
            return false;
        }
        AgentTaskStatus currentStatus = taskStateMachine.parseStatus(task.getStatus());
        return currentStatus == AgentTaskStatus.WAITING_APPROVAL;
    }

    private void failWaitingApprovalTask(PendingToolInvocationSnapshot snapshot) {
        AgentGenerationTask task = agentRepository.findGenerationTask(snapshot.projectId(), snapshot.taskId());
        if (task == null) {
            return;
        }
        AgentTaskStatus currentStatus = taskStateMachine.parseStatus(task.getStatus());
        taskStateMachine.assertTransition(currentStatus.value(), AgentTaskStatus.FAILED);
        agentRepository.updateGenerationTaskStatus(snapshot.projectId(), snapshot.taskId(), AgentTaskStatus.FAILED.value(), TIMEOUT_ERROR_MESSAGE);
        realtimeEventService.publishGenerationFailed(snapshot.projectId(), snapshot.taskId(), TIMEOUT_ERROR_CODE, TIMEOUT_ERROR_MESSAGE);
        log.warn("审批恢复交接中间态超时，已封口失败: approvalId={}, projectId={}, taskId={}, traceId={}",
                snapshot.approvalId(), snapshot.projectId(), snapshot.taskId(), snapshot.traceId());
    }
}
