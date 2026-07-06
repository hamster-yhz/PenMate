package com.penmate.backend.domain.agent.repository;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentTurn;

import java.util.List;
import java.util.Map;

public interface AgentSessionRepository {

    AgentSession findSession(Long projectId, Long sessionId);

    List<AgentTurn> listTurns(Long sessionId);

    Map<String, Object> findSessionTokenUsageSummary(Long projectId, Long sessionId);

    List<Map<String, Object>> listMessageRows(Long sessionId);

    int insertSession(AgentSession session);

    void lockSessionForTurnAppend(Long projectId, Long sessionId);

    int nextMessageSeq(Long sessionId);

    Long findTurnAssistantMessageId(Long sessionId, Long turnId);

    int nextTurnSeq(Long sessionId);

    int insertTurn(Long sessionId,
                   Long turnId,
                   Integer turnSeq,
                   Long userMessageId,
                   Long runId,
                   String turnStatus,
                   String resumeToken);

    int insertSessionMessage(Long sessionId,
                             Long turnId,
                             Long messageId,
                             String role,
                             String messageKind,
                             String contentMarkdown,
                             Integer seqNo);

    int updateTurnAssistantMessage(Long sessionId, Long turnId, Long assistantMessageId);

    int updateLastTurn(Long projectId, Long sessionId, Long turnId);

    int updateLastRun(Long projectId, Long sessionId, Long runId);

    int updateBoundStyle(Long projectId, Long sessionId, Long styleId, Long operatorId);

    int insertStyleBinding(Long projectId, Long sessionId, Long styleId, Long operatorId, String traceId);
}
