package com.penmate.backend.domain.agent.repository;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentMessage;

import java.util.List;

public interface AgentRepository {

    List<AgentConversation> listConversations(Long projectId);

    AgentConversation findConversation(Long projectId, Long conversationId);

    int insertConversation(AgentConversation conversation);

    List<AgentMessage> listMessages(Long conversationId);

    List<AgentMessage> listMessagesBeforeTurn(Long conversationId, Long turnId);

    int nextMessageSeq(Long conversationId);

    int insertMessage(AgentMessage message);

    int touchConversationLastMessage(Long conversationId);

    int incrementSessionTokenUsage(Long projectId,
                                   Long sessionId,
                                   Integer promptTokens,
                                   Integer completionTokens,
                                   Integer totalTokens);
}

