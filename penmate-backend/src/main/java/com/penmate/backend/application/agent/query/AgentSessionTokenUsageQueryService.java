package com.penmate.backend.application.agent.query;

import com.penmate.backend.application.agent.runtime.SessionTokenUsageView;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Session token 使用查询服务。
 */
@Service
@Slf4j
public class AgentSessionTokenUsageQueryService {

    private final AgentSessionRepository agentSessionRepository;

    public AgentSessionTokenUsageQueryService(AgentSessionRepository agentSessionRepository) {
        this.agentSessionRepository = agentSessionRepository;
    }

    public SessionTokenUsageView getTokenUsage(Long projectId, Long sessionId, String traceId) {
        log.info("Agent session token usage query started: projectId={}, sessionId={}, traceId={}", projectId, sessionId, traceId);
        Map<String, Object> summary = agentSessionRepository.findSessionTokenUsageSummary(projectId, sessionId);
        if (summary == null) {
            log.warn("Agent session token usage session not found: projectId={}, sessionId={}, traceId={}", projectId, sessionId, traceId);
            throw BusinessException.notFound("Agent session not found");
        }
        Integer promptTokens = intValue(valueOf(summary, "promptTokens"), 0);
        Integer completionTokens = intValue(valueOf(summary, "completionTokens"), 0);
        Integer usedTokens = intValue(valueOf(summary, "totalTokens"), promptTokens + completionTokens);
        Integer maxContextTokens = intValue(valueOf(summary, "maxContextTokens"), null);
        String modelName = stringValue(valueOf(summary, "modelName"));
        Double usageRatio = computeUsageRatio(usedTokens, maxContextTokens);
        log.info("Agent session token usage query resolved: projectId={}, sessionId={}, traceId={}, usedTokens={}, maxContextTokens={}, modelName={}",
                projectId,
                sessionId,
                traceId,
                usedTokens,
                maxContextTokens,
                modelName);
        return new SessionTokenUsageView(
                usedTokens,
                maxContextTokens,
                usageRatio,
                promptTokens,
                completionTokens,
                modelName
        );
    }

    private Double computeUsageRatio(Integer usedTokens, Integer maxContextTokens) {
        if (usedTokens == null || maxContextTokens == null || maxContextTokens <= 0) {
            return null;
        }
        return BigDecimal.valueOf(usedTokens)
                .divide(BigDecimal.valueOf(maxContextTokens), 6, RoundingMode.HALF_UP)
                .doubleValue();
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

    private Integer intValue(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
