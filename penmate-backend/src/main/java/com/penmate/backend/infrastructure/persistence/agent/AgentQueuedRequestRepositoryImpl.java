package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentQueuedRequest;
import com.penmate.backend.domain.agent.repository.AgentQueuedRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class AgentQueuedRequestRepositoryImpl implements AgentQueuedRequestRepository {
    private final AgentQueuedRequestMapper mapper;

    @Override public AgentQueuedRequest findOpen(Long projectId, Long sessionId) { return map(mapper.findOpen(projectId, sessionId)); }
    @Override public int insert(AgentQueuedRequest request) { return mapper.insert(request.requestId(), request.projectId(), request.sessionId(), request.ownerUserId(), request.requestType(), request.payloadJson()); }
    @Override public int withdraw(Long projectId, Long sessionId, Long requestId, Long ownerUserId) { return mapper.withdraw(projectId, sessionId, requestId, ownerUserId); }
    @Override public AgentQueuedRequest claimNextIdle() { return map(mapper.claimNextIdle()); }
    @Override public int markCompleted(Long requestId) { return mapper.markCompleted(requestId); }
    @Override public int requeue(Long requestId, String error) { return mapper.requeue(requestId, clip(error)); }
    @Override public int markFailed(Long requestId, String error) { return mapper.markFailed(requestId, clip(error)); }

    private AgentQueuedRequest map(Map<String, Object> row) {
        if (row == null) return null;
        return new AgentQueuedRequest(longValue(row.get("requestId")), longValue(row.get("projectId")),
                longValue(row.get("sessionId")), longValue(row.get("ownerUserId")), string(row.get("requestType")),
                string(row.get("payloadJson")), string(row.get("requestStatus")), intValue(row.get("attemptCount")),
                string(row.get("lastError")), instant(row.get("createdAt")), instant(row.get("updatedAt")));
    }

    private Long longValue(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private Integer intValue(Object value) { return value == null ? 0 : ((Number) value).intValue(); }
    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private Instant instant(Object value) { return value instanceof Timestamp timestamp ? timestamp.toInstant() : (Instant) value; }
    private String clip(String value) { return value == null ? null : value.substring(0, Math.min(value.length(), 500)); }
}
