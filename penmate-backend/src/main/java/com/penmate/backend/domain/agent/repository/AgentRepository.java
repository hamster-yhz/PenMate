package com.penmate.backend.domain.agent.repository;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentMessage;

import java.util.List;

public interface AgentRepository {

    List<AgentConversation> listConversations(Long projectId, Long userId, boolean deleted);

    AgentConversation findConversation(Long projectId, Long conversationId);

    AgentConversation findConversationIncludingDeleted(Long projectId, Long conversationId);

    int insertConversation(AgentConversation conversation);

    int updateConversationTitle(Long projectId, Long conversationId, Long userId, String title);

    int softDeleteConversation(Long projectId, Long conversationId, Long userId);

    int restoreConversation(Long projectId, Long conversationId, Long userId);

    int countActiveRuns(Long conversationId);

    String findLatestRunStatus(Long conversationId);

    List<AgentMessage> listMessages(Long conversationId);

    List<AgentMessage> listMessagesBeforeTurn(Long conversationId, Long turnId);

    int nextMessageSeq(Long conversationId);

    int insertMessage(AgentMessage message);

    int bindMessageToTurn(Long conversationId, Long messageId, Long turnId);

    int touchConversationLastMessage(Long conversationId);

    int incrementSessionTokenUsage(Long projectId,
                                   Long sessionId,
                                   Integer promptTokens,
                                   Integer completionTokens,
                                   Integer totalTokens);
}

