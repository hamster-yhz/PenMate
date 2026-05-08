package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.AgentTurn;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentSessionRepositoryImpl implements AgentSessionRepository {

    private final AgentSessionMapper agentSessionMapper;
    private final BusinessIdGenerator businessIdGenerator;

    public AgentSessionRepositoryImpl(AgentSessionMapper agentSessionMapper,
                                      BusinessIdGenerator businessIdGenerator) {
        this.agentSessionMapper = agentSessionMapper;
        this.businessIdGenerator = businessIdGenerator;
    }

    @Override
    public AgentSession findSession(Long projectId, Long sessionId) {
        return agentSessionMapper.findSession(projectId, sessionId);
    }

    @Override
    public List<AgentTurn> listTurns(Long sessionId) {
        return List.of();
    }

    @Override
    public AgentSessionRecoverySnapshot findRecoverySnapshot(Long projectId, Long sessionId) {
        AgentSession session = findSession(projectId, sessionId);
        if (session == null) {
            return null;
        }
        AgentTaskContext activeTask = null;
        if (session.getLastTaskId() != null) {
            activeTask = AgentTaskContext.recoveryOf(session.getLastTaskId(), session.getLastTaskStatus(), null);
        }
        return AgentSessionRecoverySnapshot.of(session, activeTask, null, List.of(), null);
    }

    @Override
    public int insertSession(AgentSession session) {
        return 0;
    }

    @Override
    public int updateLastRunningTask(Long projectId, Long sessionId, Long taskId) {
        return 0;
    }

    @Override
    public int updateBoundStyle(Long projectId, Long sessionId, Long styleId, Long operatorId) {
        return agentSessionMapper.updateBoundStyle(projectId, sessionId, styleId);
    }

    @Override
    public int insertStyleBinding(Long projectId, Long sessionId, Long styleId, Long operatorId, String traceId) {
        return agentSessionMapper.insertStyleBinding(businessIdGenerator.nextId(), sessionId, styleId);
    }
}
