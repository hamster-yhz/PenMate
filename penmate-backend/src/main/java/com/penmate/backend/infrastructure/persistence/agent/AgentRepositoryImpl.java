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
    public List<AgentConversation> listConversations(Long projectId, Long userId, boolean deleted) {
        return agentSessionMapper.listConversationSummaries(projectId, userId, deleted);
    }

    @Override
    public AgentConversation findConversation(Long projectId, Long conversationId) {
        return agentSessionMapper.findConversationSummary(projectId, conversationId);
    }

    @Override
    public AgentConversation findConversationIncludingDeleted(Long projectId, Long conversationId) {
        return agentSessionMapper.findConversationSummaryIncludingDeleted(projectId, conversationId);
    }

    @Override
    public int insertConversation(AgentConversation conversation) {
        return agentSessionMapper.insertConversationSummary(conversation);
    }

    @Override
    public int updateConversationTitle(Long projectId, Long conversationId, Long userId, String title) {
        return agentSessionMapper.updateConversationTitle(projectId, conversationId, userId, title);
    }

    @Override
    public int softDeleteConversation(Long projectId, Long conversationId, Long userId) {
        return agentSessionMapper.softDeleteConversation(projectId, conversationId, userId);
    }

    @Override
    public int restoreConversation(Long projectId, Long conversationId, Long userId) {
        return agentSessionMapper.restoreConversation(projectId, conversationId, userId);
    }

    @Override
    public int countActiveRuns(Long conversationId) {
        return agentSessionMapper.countActiveRuns(conversationId);
    }

    @Override
    public String findLatestRunStatus(Long conversationId) {
        return agentSessionMapper.findLatestRunStatus(conversationId);
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
    public int bindMessageToTurn(Long conversationId, Long messageId, Long turnId) {
        return agentMapper.bindMessageToTurn(conversationId, messageId, turnId);
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
