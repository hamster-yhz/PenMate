package com.penmate.backend.application.style.usecase;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import org.springframework.stereotype.Service;

/**
 * 会话风格绑定应用服务。
 * <p>Task 7 起显式维护 session -> style 绑定，作为 turn context 与 recovery snapshot 的唯一风格来源。</p>
 */
@Service
public class SessionStyleBindingAppService {

    private final AgentSessionRepository agentSessionRepository;

    public SessionStyleBindingAppService(AgentSessionRepository agentSessionRepository) {
        this.agentSessionRepository = agentSessionRepository;
    }

    /**
     * 绑定会话当前生效风格。
     * <p>返回已绑定的风格业务 ID，作为后续 turn/task 装配的唯一输入。</p>
     */
    public Long bind(Long projectId, Long sessionId, Long styleId, Long operatorId, String traceId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId must not be null");
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (styleId == null) {
            throw new IllegalArgumentException("styleId must not be null");
        }
        if (operatorId == null) {
            throw new IllegalArgumentException("operatorId must not be null");
        }
        int updated = agentSessionRepository.updateBoundStyle(projectId, sessionId, styleId, operatorId);
        if (updated != 1) {
            throw new IllegalStateException("failed to update session bound style");
        }
        int inserted = agentSessionRepository.insertStyleBinding(projectId, sessionId, styleId, operatorId, traceId);
        if (inserted != 1) {
            throw new IllegalStateException("failed to insert style binding history");
        }
        return styleId;
    }

    public Long getBoundStyleId(Long projectId, Long sessionId) {
        if (projectId == null || sessionId == null) {
            return null;
        }
        AgentSession session = agentSessionRepository.findSession(projectId, sessionId);
        return session == null ? null : session.getBoundStyleId();
    }

    public String getBoundStyleSnapshotJson(Long projectId, Long sessionId) {
        Long styleId = getBoundStyleId(projectId, sessionId);
        return styleId == null ? null : "{\"styleId\":" + styleId + "}";
    }
}
