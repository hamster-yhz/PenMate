package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.query.AgentSessionRecoveryQueryService;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 会话恢复用例应用服务。
 * <p>负责把领域 recovery snapshot 映射为应用层结果模型，并保证恢复 contract 命名单一来源。</p>
 */
@Service
public class AgentSessionRecoveryAppService {

    private final AgentSessionRecoveryQueryService agentSessionRecoveryQueryService;

    public AgentSessionRecoveryAppService(AgentSessionRecoveryQueryService agentSessionRecoveryQueryService) {
        this.agentSessionRecoveryQueryService = agentSessionRecoveryQueryService;
    }

    public AgentSessionRecoveryResult getRecovery(Long projectId, Long sessionId, String traceId) {
        return toResult(agentSessionRecoveryQueryService.getRecoverySnapshot(projectId, sessionId, traceId));
    }

    public AgentSessionRecoveryResult resumeSession(Long projectId,
                                                    Long sessionId,
                                                    Long operatorId,
                                                    String trigger,
                                                    String traceId) {
        return toResult(agentSessionRecoveryQueryService.getRecoverySnapshot(projectId, sessionId, traceId));
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
