package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentRepositoryImpl implements AgentRepository {

    private final AgentMapper agentMapper;

    public AgentRepositoryImpl(AgentMapper agentMapper) {
        this.agentMapper = agentMapper;
    }

    @Override
    public List<AgentConversation> listConversations(Long projectId) {
        return agentMapper.listConversations(projectId);
    }

    @Override
    public AgentConversation findConversation(Long projectId, Long conversationId) {
        return agentMapper.findConversation(projectId, conversationId);
    }

    @Override
    public int insertConversation(AgentConversation conversation) {
        return agentMapper.insertConversation(conversation);
    }

    @Override
    public List<AgentMessage> listMessages(Long conversationId) {
        return agentMapper.listMessages(conversationId);
    }

    @Override
    public int nextMessageSeq(Long conversationId) {
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
}
