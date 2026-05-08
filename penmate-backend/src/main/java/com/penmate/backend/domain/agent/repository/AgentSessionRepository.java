package com.penmate.backend.domain.agent.repository;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.agent.model.AgentTurn;

import java.util.List;

/**
 * 智能体会话恢复相关仓储端口。
 */
public interface AgentSessionRepository {

    /**
     * 按项目与会话业务 ID 查询会话。
     */
    AgentSession findSession(Long projectId, Long sessionId);

    /**
     * 查询会话全部轮次。
     */
    List<AgentTurn> listTurns(Long sessionId);

    /**
     * 查询用于恢复的聚合快照。
     */
    AgentSessionRecoverySnapshot findRecoverySnapshot(Long projectId, Long sessionId);

    /**
     * 新增会话。
     */
    int insertSession(AgentSession session);

    /**
     * 更新会话最近运行任务。
     */
    int updateLastRunningTask(Long projectId, Long sessionId, Long taskId);

    /**
     * 更新会话当前绑定风格。
     */
    int updateBoundStyle(Long projectId, Long sessionId, Long styleId, Long operatorId);

    /**
     * 新增会话风格绑定历史。
     */
    int insertStyleBinding(Long projectId, Long sessionId, Long styleId, Long operatorId, String traceId);
}
