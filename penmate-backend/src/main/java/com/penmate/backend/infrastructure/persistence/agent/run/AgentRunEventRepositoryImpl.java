package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Repository
public class AgentRunEventRepositoryImpl implements AgentRunEventRepository {

    private final SqlSessionFactory sqlSessionFactory;
    private final TransactionTemplate transactionTemplate;
    private final BusinessIdGenerator businessIdGenerator;

    public AgentRunEventRepositoryImpl(SqlSessionFactory sqlSessionFactory,
                                       TransactionTemplate transactionTemplate,
                                       BusinessIdGenerator businessIdGenerator) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.transactionTemplate = transactionTemplate;
        this.businessIdGenerator = businessIdGenerator;
    }

    @Override
    public AgentEvent append(Long runId, String eventType, String payloadJson) {
        return transactionTemplate.execute(status -> {
            try (SqlSession session = sqlSessionFactory.openSession(false)) {
                AgentRunEventMapper mapper = session.getMapper(AgentRunEventMapper.class);
                Long latest = mapper.lockLatestSequence(runId);
                if (latest == null) {
                    throw new IllegalArgumentException("run not found: " + runId);
                }
                Map<String, Object> identity = mapper.findRunIdentity(runId);
                long next = latest + 1L;
                AgentEvent event = new AgentEvent(
                        businessIdGenerator.nextId(),
                        runId,
                        longValue(identity, "projectId"),
                        longValue(identity, "sessionId"),
                        longValue(identity, "turnId"),
                        next,
                        1,
                        eventType,
                        payloadJson,
                        null
                );
                mapper.insert(event);
                mapper.updateLatestSequence(runId, next);
                session.commit();
                return event;
            }
        });
    }

    @Override
    public List<AgentEvent> listAfter(Long runId, Long after) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.getMapper(AgentRunEventMapper.class).listAfter(runId, after == null ? 0L : after);
        }
    }

    private Long longValue(Map<String, Object> row, String key) {
        Object value = mapValue(row, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Object mapValue(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        String snakeKey = toSnakeCase(key).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String candidate = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            if (candidate.equals(lowerKey) || candidate.equals(snakeKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String toSnakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isUpperCase(current) && builder.length() > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(current));
        }
        return builder.toString();
    }
}
