package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentSessionContextSummary;
import com.penmate.backend.domain.agent.repository.AgentSessionContextSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class AgentSessionContextSummaryRepositoryImpl implements AgentSessionContextSummaryRepository {
    private final AgentSessionContextSummaryMapper mapper;

    @Override
    public AgentSessionContextSummary find(Long sessionId) {
        Map<String, Object> row = mapper.find(sessionId);
        if (row == null) return null;
        return new AgentSessionContextSummary(number(row.get("sessionId")), number(row.get("projectId")),
                number(row.get("ownerUserId")), text(row.get("summaryJson")), integer(row.get("cutoffMessageSeq")),
                integer(row.get("promptTokens")), integer(row.get("completionTokens")), instant(row.get("updatedAt")));
    }

    @Override
    public int upsert(AgentSessionContextSummary summary) {
        return mapper.upsert(summary.sessionId(), summary.projectId(), summary.ownerUserId(), summary.summaryJson(),
                summary.cutoffMessageSeq(), summary.promptTokens(), summary.completionTokens());
    }

    private Long number(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private Integer integer(Object value) { return value == null ? 0 : ((Number) value).intValue(); }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private Instant instant(Object value) { return value instanceof Timestamp timestamp ? timestamp.toInstant() : (Instant) value; }
}
