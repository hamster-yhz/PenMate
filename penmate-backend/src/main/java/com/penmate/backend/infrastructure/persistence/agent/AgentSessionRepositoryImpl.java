package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.AgentTurn;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.sql.Clob;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
        AgentSession session = agentSessionMapper.findSession(projectId, sessionId);
        if (session != null) {
            return session;
        }
        Map<String, Object> row = agentSessionMapper.findSessionRow(projectId, sessionId);
        return toSession(row);
    }

    @Override
    public List<AgentTurn> listTurns(Long sessionId) {
        return agentSessionMapper.listTurnRows(sessionId).stream()
                .map(this::toTurn)
                .toList();
    }

    @Override
    public AgentSessionRecoverySnapshot findRecoverySnapshot(Long projectId, Long sessionId) {
        AgentSession session = findSession(projectId, sessionId);
        if (session == null) {
            return null;
        }
        AgentTaskContext activeTask = buildActiveTask(session);
        List<Object> messages = agentSessionMapper.listMessageRows(sessionId).stream()
                .<Object>map(row -> new LinkedHashMap<>(row))
                .toList();
        String workbenchContext = activeTask == null ? null : activeTask.getOutlineSnapshotJson();
        return AgentSessionRecoverySnapshot.of(session, activeTask, null, messages, workbenchContext);
    }

    @Override
    public AgentTaskContext findTaskByTurnId(Long projectId, Long sessionId, Long turnId) {
        Map<String, Object> taskRow = agentSessionMapper.findTaskRowByTurnId(projectId, sessionId, turnId);
        if (taskRow == null) {
            return null;
        }
        AgentTaskContext context = new AgentTaskContext();
        setField(context, "turnId", longValue(valueOf(taskRow, "turnId")));
        setField(context, "taskId", longValue(valueOf(taskRow, "taskId")));
        setField(context, "taskStatus", stringValue(valueOf(taskRow, "taskStatus")));
        setField(context, "contextId", longValue(valueOf(taskRow, "requestContextId")));
        setField(context, "activeApprovalId", longValue(valueOf(taskRow, "activeApprovalId")));
        return context;
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
    public int nextTurnSeq(Long sessionId) {
        lockSessionForTurnAppend(null, sessionId);
        return agentSessionMapper.maxTurnSeq(sessionId) + 1;
    }

    @Override
    public int insertTurn(Long sessionId,
                          Long turnId,
                          Integer turnSeq,
                          Long userMessageId,
                          Long taskId,
                          String turnStatus,
                          String resumeToken) {
        return agentSessionMapper.insertTurn(sessionId, turnId, turnSeq, userMessageId, taskId, turnStatus, resumeToken);
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
    public int insertRuntimeTask(Long taskId,
                                 Long sessionId,
                                 Long turnId,
                                 Long projectId,
                                 String taskType,
                                 String taskStatus,
                                 String promptSnapshot,
                                 Long requestContextId,
                                 String traceId) {
        return agentSessionMapper.insertRuntimeTask(taskId, sessionId, turnId, projectId, taskType, taskStatus, promptSnapshot, requestContextId, traceId);
    }

    @Override
    public int updateRuntimeTaskTurnLink(Long projectId, Long taskId, Long turnId) {
        return agentSessionMapper.updateRuntimeTaskTurnLink(projectId, taskId, turnId);
    }

    @Override
    public int insertTaskContext(AgentTaskContext taskContext) {
        return agentSessionMapper.insertTaskContext(taskContext);
    }

    @Override
    public int updateLastTurn(Long projectId, Long sessionId, Long turnId) {
        return agentSessionMapper.updateLastTurn(projectId, sessionId, turnId);
    }

    @Override
    public int updateLastRunningTask(Long projectId, Long sessionId, Long taskId) {
        return agentSessionMapper.updateLastRunningTask(projectId, sessionId, taskId);
    }

    @Override
    public int updateBoundStyle(Long projectId, Long sessionId, Long styleId, Long operatorId) {
        return agentSessionMapper.updateBoundStyle(projectId, sessionId, styleId);
    }

    @Override
    public int insertStyleBinding(Long projectId, Long sessionId, Long styleId, Long operatorId, String traceId) {
        return agentSessionMapper.insertStyleBinding(businessIdGenerator.nextId(), sessionId, styleId);
    }

    private AgentTaskContext buildActiveTask(AgentSession session) {
        if (session == null || session.getLastTaskId() == null) {
            return null;
        }
        Map<String, Object> taskRow = agentSessionMapper.findTaskRow(session.getSessionId(), session.getLastTaskId());
        if (taskRow == null) {
            return AgentTaskContext.recoveryOf(session.getLastTaskId(), session.getLastTaskStatus(), null);
        }
        Map<String, Object> contextRow = agentSessionMapper.findTaskContextRow(longValue(valueOf(taskRow, "taskId")));
        AgentTaskContext context = new AgentTaskContext();
        setField(context, "contextId", longValue(valueOf(contextRow, "contextId")));
        setField(context, "turnId", longValue(valueOf(taskRow, "turnId")));
        setField(context, "taskId", longValue(valueOf(taskRow, "taskId")));
        setField(context, "taskStatus", stringValue(valueOf(taskRow, "taskStatus")));
        setField(context, "activeApprovalId", longValue(valueOf(taskRow, "activeApprovalId")));
        setField(context, "chapterId", longValue(valueOf(contextRow, "chapterId")));
        setField(context, "selectedText", stringValue(valueOf(contextRow, "selectedText")));
        setField(context, "outlineSnapshotJson", stringValue(valueOf(contextRow, "outlineSnapshotJson")));
        setField(context, "cardsSnapshotJson", stringValue(valueOf(contextRow, "cardsSnapshotJson")));
        setField(context, "ragSnapshotJson", stringValue(valueOf(contextRow, "ragSnapshotJson")));
        setField(context, "pluginBindingsJson", stringValue(valueOf(contextRow, "pluginBindingsJson")));
        setField(context, "styleSnapshotJson", stringValue(valueOf(contextRow, "styleSnapshotJson")));
        setField(context, "modelSnapshotJson", stringValue(valueOf(contextRow, "modelSnapshotJson")));
        setField(context, "contextHash", stringValue(valueOf(contextRow, "contextHash")));
        return context;
    }

    private AgentSession toSession(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        AgentSession session = AgentSession.active(
                longValue(valueOf(row, "sessionId")),
                longValue(valueOf(row, "projectId")),
                longValue(valueOf(row, "ownerUserId")),
                stringValue(valueOf(row, "title"))
        );
        setField(session, "id", longValue(valueOf(row, "id")));
        setField(session, "sessionStatus", stringValue(valueOf(row, "sessionStatus")));
        setField(session, "boundStyleId", longValue(valueOf(row, "boundStyleId")));
        setField(session, "activeContextVersion", intValue(valueOf(row, "activeContextVersion")));
        setField(session, "lastTurnId", longValue(valueOf(row, "lastTurnId")));
        setField(session, "lastTaskId", longValue(valueOf(row, "lastTaskId")));
        setField(session, "lastTaskStatus", resolveLastTaskStatus(session));
        setField(session, "resumedAt", localDateTime(valueOf(row, "resumedAt")));
        setField(session, "createdAt", localDateTime(valueOf(row, "createdAt")));
        setField(session, "updatedAt", localDateTime(valueOf(row, "updatedAt")));
        return session;
    }

    private String resolveLastTaskStatus(AgentSession session) {
        if (session == null || session.getLastTaskId() == null) {
            return null;
        }
        Map<String, Object> taskRow = agentSessionMapper.findTaskRow(session.getSessionId(), session.getLastTaskId());
        return stringValue(taskRow == null ? null : valueOf(taskRow, "taskStatus"));
    }

    private AgentTurn toTurn(Map<String, Object> row) {
        AgentTurn turn = new AgentTurn();
        setField(turn, "sessionId", longValue(valueOf(row, "sessionId")));
        setField(turn, "turnId", longValue(valueOf(row, "turnId")));
        setField(turn, "turnSeq", intValue(valueOf(row, "turnSeq")));
        setField(turn, "userMessageId", longValue(valueOf(row, "userMessageId")));
        setField(turn, "assistantMessageId", longValue(valueOf(row, "assistantMessageId")));
        setField(turn, "taskId", longValue(valueOf(row, "taskId")));
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
        if (value == null) {
            return null;
        }
        if (value instanceof Clob clob) {
            try {
                return clob.getSubString(1, (int) clob.length());
            } catch (Exception ex) {
                throw new IllegalStateException("failed to read clob value", ex);
            }
        }
        return String.valueOf(value);
    }

    private LocalDateTime localDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
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
