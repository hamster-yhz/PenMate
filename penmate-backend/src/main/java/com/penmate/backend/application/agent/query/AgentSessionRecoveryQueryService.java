package com.penmate.backend.application.agent.query;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 会话恢复查询服务。
 * <p>优先复用仓储层已聚合好的 recovery snapshot；若 pendingApproval 缺失，则基于待恢复审批快照补齐，
 * 以保证 WAITING_APPROVAL 场景可被工作台直接恢复。</p>
 */
@Service
public class AgentSessionRecoveryQueryService {

    private final AgentSessionRepository agentSessionRepository;
    private final PendingToolInvocationRepository pendingToolInvocationRepository;

    public AgentSessionRecoveryQueryService(AgentSessionRepository agentSessionRepository,
                                            PendingToolInvocationRepository pendingToolInvocationRepository) {
        this.agentSessionRepository = agentSessionRepository;
        this.pendingToolInvocationRepository = pendingToolInvocationRepository;
    }

    public AgentSessionRecoverySnapshot getRecoverySnapshot(Long projectId, Long sessionId, String traceId) {
        AgentSessionRecoverySnapshot snapshot = agentSessionRepository.findRecoverySnapshot(projectId, sessionId);
        if (snapshot == null) {
            AgentSession session = agentSessionRepository.findSession(projectId, sessionId);
            if (session == null) {
                return null;
            }
            snapshot = AgentSessionRecoverySnapshot.of(
                    session,
                    buildActiveTaskFromSession(session),
                    null,
                    List.of(),
                    null
            );
        }

        if (snapshot.getPendingApproval() != null) {
            return snapshot;
        }
        AgentTaskContext activeTask = snapshot.getActiveTask();
        if (activeTask == null || activeTask.getActiveApprovalId() == null) {
            return snapshot;
        }
        PendingToolInvocationSnapshot pendingSnapshot = pendingToolInvocationRepository.findByApprovalId(activeTask.getActiveApprovalId());
        if (pendingSnapshot == null) {
            return snapshot;
        }
        return AgentSessionRecoverySnapshot.of(
                snapshot.getSession(),
                decorateTaskStatus(activeTask, snapshot.getSession()),
                pendingApprovalView(pendingSnapshot, traceId),
                snapshot.getMessages(),
                snapshot.getWorkbenchContext()
        );
    }

    private AgentTaskContext buildActiveTaskFromSession(AgentSession session) {
        if (session.getLastTaskId() == null) {
            return null;
        }
        return AgentTaskContext.recoveryOf(session.getLastTaskId(), session.getLastTaskStatus(), null);
    }

    private AgentTaskContext decorateTaskStatus(AgentTaskContext activeTask, AgentSession session) {
        if (activeTask.getTaskStatus() != null) {
            return activeTask;
        }
        return AgentTaskContext.recoveryOf(
                activeTask.getTaskId(),
                session == null ? null : session.getLastTaskStatus(),
                activeTask.getActiveApprovalId()
        );
    }

    private Map<String, Object> pendingApprovalView(PendingToolInvocationSnapshot snapshot, String traceId) {
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("approvalId", snapshot.approvalId());
        approval.put("taskId", snapshot.taskId());
        approval.put("sessionId", snapshot.conversationId());
        approval.put("toolCallId", snapshot.toolCallId());
        approval.put("pendingStatus", snapshot.status());
        approval.put("resumeMode", snapshot.resumeMode());
        approval.put("approvalSummary", snapshot.approvalSummaryJson());
        approval.put("traceId", traceId == null || traceId.isBlank() ? snapshot.traceId() : traceId);
        return approval;
    }

}
