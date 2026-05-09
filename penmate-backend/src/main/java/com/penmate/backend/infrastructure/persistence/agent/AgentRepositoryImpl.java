package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Agent 仓储 MyBatis 实现。
 * <p>负责 Agent 会话、消息、生成任务等聚合的数据库读写，并保持旧仓储接口与当前 session-centric schema 兼容。</p>
 */
@Repository
public class AgentRepositoryImpl implements AgentRepository {

    private final AgentMapper agentMapper;
    private final AgentSessionMapper agentSessionMapper;

    public AgentRepositoryImpl(AgentMapper agentMapper,
                               AgentSessionMapper agentSessionMapper) {
        this.agentMapper = agentMapper;
        this.agentSessionMapper = agentSessionMapper;
    }

    @Override
    public List<AgentConversation> listConversations(Long projectId) {
        return agentSessionMapper.listConversationSummaries(projectId);
    }

    @Override
    public AgentConversation findConversation(Long projectId, Long conversationId) {
        return agentSessionMapper.findConversationSummary(projectId, conversationId);
    }

    @Override
    public int insertConversation(AgentConversation conversation) {
        return agentSessionMapper.insertConversationSummary(conversation);
    }

    @Override
    public List<AgentMessage> listMessages(Long conversationId) {
        return agentMapper.listMessages(conversationId);
    }

    @Override
    public int nextMessageSeq(Long conversationId) {
        agentSessionMapper.lockSessionForTurnAppend(null, conversationId);
        return agentMapper.maxMessageSeq(conversationId) + 1;
    }

    @Override
    public int insertMessage(AgentMessage message) {
        return agentMapper.insertMessage(message);
    }

    @Override
    public int touchConversationLastMessage(Long conversationId) {
        return agentMapper.touchConversationLastMessage(conversationId);
    }

    @Override
    public int insertGenerationTask(AgentGenerationTask task) {
        return agentMapper.insertGenerationTask(task);
    }

    @Override
    public AgentGenerationTask findGenerationTask(Long projectId, Long taskId) {
        return agentMapper.findGenerationTask(projectId, taskId);
    }

    @Override
    public int updateGenerationTaskStatus(Long projectId, Long taskId, String status, String errorMsg) {
        return agentMapper.updateGenerationTaskStatus(projectId, taskId, status, errorMsg);
    }

    @Override
    public int updateGenerationTaskRuntime(Long projectId, Long taskId, String tokenUsageJson, String costJson, String traceId) {
        return agentMapper.updateGenerationTaskRuntime(projectId, taskId, tokenUsageJson, costJson, traceId);
    }
}
