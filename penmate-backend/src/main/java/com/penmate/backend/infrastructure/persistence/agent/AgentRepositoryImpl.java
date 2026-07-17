package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

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
    public List<AgentMessage> listMessagesBeforeTurn(Long conversationId, Long turnId) {
        return agentMapper.listMessagesBeforeTurn(conversationId, turnId);
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
    public int incrementSessionTokenUsage(Long projectId,
                                          Long sessionId,
                                          Integer promptTokens,
                                          Integer completionTokens,
                                          Integer totalTokens) {
        return agentSessionMapper.incrementSessionTokenUsage(projectId, sessionId, promptTokens, completionTokens, totalTokens);
    }
}
