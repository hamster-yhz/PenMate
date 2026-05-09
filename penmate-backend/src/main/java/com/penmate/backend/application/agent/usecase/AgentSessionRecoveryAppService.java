package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.query.AgentSessionRecoveryQueryService;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 会话恢复用例应用服务。
 * <p>负责把领域 recovery snapshot 映射为应用层结果模型，并保证恢复 contract 命名单一来源。</p>
 */
@Service
@Slf4j
public class AgentSessionRecoveryAppService {

    private final AgentSessionRecoveryQueryService agentSessionRecoveryQueryService;

    public AgentSessionRecoveryAppService(AgentSessionRecoveryQueryService agentSessionRecoveryQueryService) {
        this.agentSessionRecoveryQueryService = agentSessionRecoveryQueryService;
    }

    public AgentSessionRecoveryResult getRecovery(Long projectId, Long sessionId, String traceId) {
        log.info("Agent session recovery requested: projectId={}, sessionId={}, traceId={}", projectId, sessionId, traceId);
        AgentSessionRecoveryResult result = toResult(agentSessionRecoveryQueryService.getRecoverySnapshot(projectId, sessionId, traceId));
        log.info("Agent session recovery resolved: projectId={}, sessionId={}, traceId={}, hasSession={}, activeTaskId={}, activeTaskStatus={}, messageCount={}, hasPendingApproval={}, hasWorkbenchContext={}",
                projectId,
                sessionId,
                traceId,
                result.session() != null,
                result.activeTask() == null ? null : result.activeTask().taskId(),
                result.activeTask() == null ? null : result.activeTask().taskStatus(),
                result.messages() == null ? 0 : result.messages().size(),
                result.pendingApproval() != null,
                result.workbenchContext() != null);
        return result;
    }

    public AgentSessionRecoveryResult resumeSession(Long projectId,
                                                    Long sessionId,
                                                    Long operatorId,
                                                    String trigger,
                                                    String traceId) {
        log.info("Agent session resume requested: projectId={}, sessionId={}, operatorId={}, trigger={}, traceId={}",
                projectId,
                sessionId,
                operatorId,
                trigger,
                traceId);
        AgentSessionRecoveryResult result = toResult(agentSessionRecoveryQueryService.getRecoverySnapshot(projectId, sessionId, traceId));
        log.info("Agent session resume resolved: projectId={}, sessionId={}, traceId={}, hasSession={}, activeTaskId={}, activeTaskStatus={}, hasPendingApproval={}",
                projectId,
                sessionId,
                traceId,
                result.session() != null,
                result.activeTask() == null ? null : result.activeTask().taskId(),
                result.activeTask() == null ? null : result.activeTask().taskStatus(),
                result.pendingApproval() != null);
        return result;
    }

    private AgentSessionRecoveryResult toResult(AgentSessionRecoverySnapshot snapshot) {
        if (snapshot == null) {
            return new AgentSessionRecoveryResult(null, null, null, List.of(), null);
        }
        AgentSession session = snapshot.getSession();
        AgentTaskContext activeTask = snapshot.getActiveTask();
        List<Object> messages = new ArrayList<>();
        if (snapshot.getMessages() != null) {
            messages.addAll(snapshot.getMessages());
        }
        return new AgentSessionRecoveryResult(
                session == null ? null : new AgentSessionRecoveryResult.SessionView(
                        session.getSessionId(),
                        session.getTitle(),
                        session.getStatus(),
                        session.getBoundStyleId() == null
                                ? null
                                : new AgentSessionRecoveryResult.BoundStyleView(session.getBoundStyleId(), null),
                        session.getLastTaskStatus()
                ),
                activeTask == null ? null : new AgentSessionRecoveryResult.ActiveTaskView(
                        activeTask.getTurnId(),
                        activeTask.getTaskId(),
                        activeTask.getTaskStatus(),
                        activeTask.getContextId()
                ),
                snapshot.getPendingApproval(),
                messages,
                snapshot.getWorkbenchContext()
        );
    }
}
