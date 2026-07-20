package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentTurn;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, Object> row = agentSessionMapper.findSessionRow(projectId, sessionId);
        if (row != null) {
            return toSession(row);
        }
        return agentSessionMapper.findSession(projectId, sessionId);
    }

    @Override
    public List<AgentTurn> listTurns(Long sessionId) {
        return agentSessionMapper.listTurnRows(sessionId).stream()
                .map(this::toTurn)
                .toList();
    }

    @Override
    public Map<String, Object> findSessionTokenUsageSummary(Long projectId, Long sessionId) {
        Map<String, Object> sessionRow = agentSessionMapper.findSessionRow(projectId, sessionId);
        if (sessionRow == null) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("promptTokens", intValue(valueOf(sessionRow, "totalPromptTokens")));
        summary.put("completionTokens", intValue(valueOf(sessionRow, "totalCompletionTokens")));
        summary.put("totalTokens", intValue(valueOf(sessionRow, "totalTokens")));
        return summary;
    }

    @Override
    public List<Map<String, Object>> listMessageRows(Long sessionId) {
        return agentSessionMapper.listMessageRows(sessionId);
    }

    @Override
    public int insertSession(AgentSession session) {
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId(session.getSessionId());
        conversation.setProjectId(session.getProjectId());
        conversation.setUserId(session.getOwnerUserId());
        conversation.setTitle(session.getTitle());
        conversation.setStatus(session.getSessionStatus());
        return agentSessionMapper.insertConversationSummary(conversation);
    }

    @Override
    public void lockSessionForTurnAppend(Long projectId, Long sessionId) {
        agentSessionMapper.lockSessionForTurnAppend(projectId, sessionId);
    }

    @Override
    public int nextMessageSeq(Long sessionId) {
        lockSessionForTurnAppend(null, sessionId);
        return agentSessionMapper.maxMessageSeq(sessionId) + 1;
    }

    @Override
    public Long findTurnAssistantMessageId(Long sessionId, Long turnId) {
        return agentSessionMapper.findTurnAssistantMessageId(sessionId, turnId);
    }

    @Override
    public int nextTurnSeq(Long sessionId) {
        lockSessionForTurnAppend(null, sessionId);
        return agentSessionMapper.maxTurnSeq(sessionId) + 1;
    }

    @Override
    public int insertTurn(Long sessionId,
                          Long turnId,
                          Integer turnSeq,
                          Long userMessageId,
                          Long runId,
                          String turnStatus,
                          String resumeToken) {
        return agentSessionMapper.insertTurn(sessionId, turnId, turnSeq, userMessageId, runId, turnStatus, resumeToken);
    }

    @Override
    public int insertSessionMessage(Long sessionId,
                                    Long turnId,
                                    Long messageId,
                                    String role,
                                    String messageKind,
                                    String contentMarkdown,
                                    Integer seqNo) {
        return agentSessionMapper.insertSessionMessage(sessionId, turnId, messageId, role, messageKind, contentMarkdown, seqNo);
    }

    @Override
    public int updateTurnAssistantMessage(Long sessionId, Long turnId, Long assistantMessageId) {
        return agentSessionMapper.updateTurnAssistantMessage(sessionId, turnId, assistantMessageId);
    }

    @Override
    public int updateMessageContent(Long sessionId, Long messageId, String contentMarkdown) {
        return agentSessionMapper.updateMessageContent(sessionId, messageId, contentMarkdown);
    }

    @Override
    public int updateLastTurn(Long projectId, Long sessionId, Long turnId) {
        return agentSessionMapper.updateLastTurn(projectId, sessionId, turnId);
    }

    @Override
    public int updateLastRun(Long projectId, Long sessionId, Long runId) {
        return agentSessionMapper.updateLastRun(projectId, sessionId, runId);
    }

    @Override
    public int rebindTurnRun(Long sessionId, Long turnId, Long expectedRunId, Long successorRunId) {
        return agentSessionMapper.rebindTurnRun(sessionId, turnId, expectedRunId, successorRunId);
    }

    @Override
    public int updateBoundStyle(Long projectId, Long sessionId, Long styleId, Long operatorId) {
        return agentSessionMapper.updateBoundStyle(projectId, sessionId, styleId);
    }

    @Override
    public int insertStyleBinding(Long projectId, Long sessionId, Long styleId, Long operatorId, String traceId) {
        return agentSessionMapper.insertStyleBinding(businessIdGenerator.nextId(), sessionId, styleId);
    }

    @Override
    public int deactivateStyleBindings(Long sessionId) {
        return agentSessionMapper.deactivateStyleBindings(sessionId);
    }

    @Override
    public Long findActiveStyleBindingRevision(Long sessionId) {
        return agentSessionMapper.findActiveStyleBindingRevision(sessionId);
    }

    private AgentSession toSession(Map<String, Object> row) {
        AgentSession session = AgentSession.active(
                longValue(valueOf(row, "sessionId")),
                longValue(valueOf(row, "projectId")),
                longValue(valueOf(row, "ownerUserId")),
                stringValue(valueOf(row, "title"))
        );
        setField(session, "id", longValue(valueOf(row, "id")));
        setField(session, "sessionStatus", stringValue(valueOf(row, "sessionStatus")));
        setField(session, "boundStyleId", longValue(valueOf(row, "boundStyleId")));
        setField(session, "activeContextEpochId", longValue(valueOf(row, "activeContextEpochId")));
        setField(session, "lastTurnId", longValue(valueOf(row, "lastTurnId")));
        setField(session, "lastRunId", longValue(valueOf(row, "lastRunId")));
        setField(session, "lastRunStatus", null);
        setField(session, "resumedAt", localDateTime(valueOf(row, "resumedAt")));
        setField(session, "createdAt", localDateTime(valueOf(row, "createdAt")));
        setField(session, "updatedAt", localDateTime(valueOf(row, "updatedAt")));
        return session;
    }

    private AgentTurn toTurn(Map<String, Object> row) {
        AgentTurn turn = new AgentTurn();
        setField(turn, "sessionId", longValue(valueOf(row, "sessionId")));
        setField(turn, "turnId", longValue(valueOf(row, "turnId")));
        setField(turn, "turnSeq", intValue(valueOf(row, "turnSeq")));
        setField(turn, "userMessageId", longValue(valueOf(row, "userMessageId")));
        setField(turn, "assistantMessageId", longValue(valueOf(row, "assistantMessageId")));
        setField(turn, "runId", longValue(valueOf(row, "runId")));
        setField(turn, "turnStatus", stringValue(valueOf(row, "turnStatus")));
        setField(turn, "resumeToken", stringValue(valueOf(row, "resumeToken")));
        setField(turn, "createdAt", localDateTime(valueOf(row, "createdAt")));
        return turn;
    }

    private Object valueOf(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String normalizedExpected = normalizeKey(key);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (normalizedExpected.equals(normalizeKey(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String normalizeKey(String key) {
        return key == null ? null : key.replace("_", "").toLowerCase();
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Instant localDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        return null;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to set field: " + fieldName, ex);
        }
    }
}
