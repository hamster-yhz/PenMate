package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.model.AgentSession;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Locale;

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
        Map<String, Object> taskRow = agentMapper.findGenerationTask(projectId, taskId);
        if (taskRow == null) {
            return null;
        }
        AgentGenerationTask task = new AgentGenerationTask();
        applyTaskRow(task, taskRow);
        AgentSession session = agentSessionMapper.findSession(projectId, task.getConversationId());
        if (session != null) {
            task.setUserId(session.getOwnerUserId());
        }
        Map<String, Object> contextRow = agentSessionMapper.findTaskContextRow(taskId);
        if (contextRow != null) {
            Object chapterId = mapValue(contextRow, "chapterId");
            if (chapterId instanceof Number number) {
                task.setChapterId(number.longValue());
            }
            Object modelSnapshotJson = mapValue(contextRow, "modelSnapshotJson");
            if (modelSnapshotJson != null) {
                applyModelSnapshot(task, String.valueOf(modelSnapshotJson));
            }
        }
        return task;
    }

    private void applyTaskRow(AgentGenerationTask task, Map<String, Object> taskRow) {
        if (task == null || taskRow == null) {
            return;
        }
        task.setId(longValue(mapValue(taskRow, "id")));
        task.setTaskId(longValue(mapValue(taskRow, "taskId")));
        task.setProjectId(longValue(mapValue(taskRow, "projectId")));
        task.setConversationId(longValue(mapValue(taskRow, "conversationId")));
        task.setTaskType(stringValue(mapValue(taskRow, "taskType")));
        task.setTraceId(stringValue(mapValue(taskRow, "traceId")));
        task.setStatus(stringValue(mapValue(taskRow, "status")));
        task.setStartedAt(localDateTime(mapValue(taskRow, "startedAt")));
        task.setFinishedAt(localDateTime(mapValue(taskRow, "finishedAt")));
        task.setCreatedAt(localDateTime(mapValue(taskRow, "createdAt")));
    }

    private Object mapValue(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        String snakeCaseKey = toSnakeCase(key).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String candidateKey = entry.getKey().toLowerCase(Locale.ROOT);
            if (candidateKey.equals(lowerKey) || candidateKey.equals(snakeCaseKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String toSnakeCase(String key) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char current = key.charAt(i);
            if (Character.isUpperCase(current)) {
                if (builder.length() > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(current));
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private void applyModelSnapshot(AgentGenerationTask task, String modelSnapshotJson) {
        if (task == null || modelSnapshotJson == null || modelSnapshotJson.isBlank()) {
            return;
        }
        Long operatorId = extractJsonLong(modelSnapshotJson, "operatorId");
        if (task.getUserId() == null && operatorId != null) {
            task.setUserId(operatorId);
        }
        Long modelConfigId = extractJsonLong(modelSnapshotJson, "modelConfigId");
        if (modelConfigId != null) {
            task.setModelConfigId(modelConfigId);
        }
    }

    private Long extractJsonLong(String json, String fieldName) {
        if (json == null || fieldName == null) {
            return null;
        }
        String pattern = "\"" + fieldName + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return null;
        }
        int valueStart = start + pattern.length();
        int valueEnd = valueStart;
        while (valueEnd < json.length()) {
            char current = json.charAt(valueEnd);
            if ((current >= '0' && current <= '9') || current == '-') {
                valueEnd++;
                continue;
            }
            break;
        }
        if (valueEnd <= valueStart) {
            return null;
        }
        return Long.parseLong(json.substring(valueStart, valueEnd));
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private java.time.LocalDateTime localDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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
