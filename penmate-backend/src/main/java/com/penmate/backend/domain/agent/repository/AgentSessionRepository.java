package com.penmate.backend.domain.agent.repository;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.AgentTurn;

import java.util.List;
import java.util.Map;

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
     * 通过 session + turn 查询运行中任务。
     */
    AgentTaskContext findTaskByTurnId(Long projectId, Long sessionId, Long turnId);

    /**
     * 查询会话累计 token 与模型上下文窗口摘要。
     */
    Map<String, Object> findSessionTokenUsageSummary(Long projectId, Long sessionId);

    /**
     * 新增会话。
     */
    int insertSession(AgentSession session);

    /**
     * 为 turn 追加锁定会话行，收窄同 session 下的序号竞争窗口。
     */
    void lockSessionForTurnAppend(Long projectId, Long sessionId);

    /**
     * 计算会话内下一个 turn 序号。
     */
    int nextTurnSeq(Long sessionId);

    /**
     * 新增 turn。
     */
    int insertTurn(Long sessionId,
                   Long turnId,
                   Integer turnSeq,
                   Long userMessageId,
                   Long taskId,
                   String turnStatus,
                   String resumeToken);

    /**
     * 新增恢复链消息记录。
     */
    int insertSessionMessage(Long sessionId,
                             Long turnId,
                             Long messageId,
                             String role,
                             String messageKind,
                             String contentMarkdown,
                             Integer seqNo);

    /**
     * 新增恢复链任务记录。
     */
    int insertRuntimeTask(Long taskId,
                          Long sessionId,
                          Long turnId,
                          Long projectId,
                          String taskType,
                          String taskStatus,
                          String promptSnapshot,
                          Long requestContextId,
                          String traceId);

    /**
     * 回填运行时任务关联的真实 turn。
     */
    int updateRuntimeTaskTurnLink(Long projectId, Long taskId, Long turnId);

    /**
     * 新增任务上下文快照。
     */
    int insertTaskContext(AgentTaskContext taskContext);

    /**
     * 更新会话最近 turn 指针。
     */
    int updateLastTurn(Long projectId, Long sessionId, Long turnId);

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
