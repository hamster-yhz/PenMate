package com.penmate.backend.application.agent.query;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AgentSessionRecoveryQueryService {

    private final AgentSessionRepository agentSessionRepository;
    private final PendingToolInvocationRepository pendingToolInvocationRepository;

    public AgentSessionRecoveryQueryService(AgentSessionRepository agentSessionRepository,
                                            PendingToolInvocationRepository pendingToolInvocationRepository) {
        this.agentSessionRepository = agentSessionRepository;
        this.pendingToolInvocationRepository = pendingToolInvocationRepository;
    }

    public AgentSessionRecoverySnapshot getRecoverySnapshot(Long projectId, Long sessionId, String traceId) {
        log.info("Agent recovery snapshot query started: projectId={}, sessionId={}, traceId={}", projectId, sessionId, traceId);
        AgentSessionRecoverySnapshot snapshot = agentSessionRepository.findRecoverySnapshot(projectId, sessionId);
        if (snapshot == null) {
            log.info("Agent recovery snapshot not found, fallback to session lookup: projectId={}, sessionId={}, traceId={}", projectId, sessionId, traceId);
            AgentSession session = agentSessionRepository.findSession(projectId, sessionId);
            if (session == null) {
                log.warn("Agent recovery session not found: projectId={}, sessionId={}, traceId={}", projectId, sessionId, traceId);
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

        log.info("Agent recovery snapshot loaded: projectId={}, sessionId={}, traceId={}, hasSession={}, activeTaskId={}, activeTaskStatus={}, activeApprovalId={}, messageCount={}, hasPendingApproval={}, hasWorkbenchContext={}",
                projectId,
                sessionId,
                traceId,
                snapshot.getSession() != null,
                snapshot.getActiveTask() == null ? null : snapshot.getActiveTask().getTaskId(),
                snapshot.getActiveTask() == null ? null : snapshot.getActiveTask().getTaskStatus(),
                snapshot.getActiveTask() == null ? null : snapshot.getActiveTask().getActiveApprovalId(),
                snapshot.getMessages() == null ? 0 : snapshot.getMessages().size(),
                snapshot.getPendingApproval() != null,
                snapshot.getWorkbenchContext() != null);

        if (snapshot.getPendingApproval() != null) {
            log.info("Agent recovery snapshot already contains pending approval: projectId={}, sessionId={}, traceId={}, activeTaskId={}",
                    projectId,
                    sessionId,
                    traceId,
                    snapshot.getActiveTask() == null ? null : snapshot.getActiveTask().getTaskId());
            return snapshot;
        }
        AgentTaskContext activeTask = snapshot.getActiveTask();
        if (activeTask == null || activeTask.getActiveApprovalId() == null) {
            log.info("Agent recovery snapshot has no active approval to enrich: projectId={}, sessionId={}, traceId={}, activeTaskId={}",
                    projectId,
                    sessionId,
                    traceId,
                    activeTask == null ? null : activeTask.getTaskId());
            return snapshot;
        }
        PendingToolInvocationSnapshot pendingSnapshot = pendingToolInvocationRepository.findByApprovalId(activeTask.getActiveApprovalId());
        if (pendingSnapshot == null) {
            log.warn("Agent recovery pending approval snapshot missing: projectId={}, sessionId={}, traceId={}, activeTaskId={}, approvalId={}",
                    projectId,
                    sessionId,
                    traceId,
                    activeTask.getTaskId(),
                    activeTask.getActiveApprovalId());
            return snapshot;
        }
        log.info("Agent recovery pending approval enriched: projectId={}, sessionId={}, traceId={}, activeTaskId={}, approvalId={}, pendingStatus={}",
                projectId,
                sessionId,
                traceId,
                activeTask.getTaskId(),
                activeTask.getActiveApprovalId(),
                pendingSnapshot.status());
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
