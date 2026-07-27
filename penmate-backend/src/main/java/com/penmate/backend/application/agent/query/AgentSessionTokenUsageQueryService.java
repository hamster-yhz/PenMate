package com.penmate.backend.application.agent.query;

import com.penmate.backend.application.agent.runtime.SessionTokenUsageView;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.model.AgentSessionContextUsageSource;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
        AgentSessionContextUsageSource source = agentSessionRepository.findSessionContextUsageSource(projectId, sessionId);
        if (source == null) {
            log.warn("Agent session token usage session not found: projectId={}, sessionId={}, traceId={}", projectId, sessionId, traceId);
            throw BusinessException.notFound("Agent session not found");
        }
        boolean providerAnchored = source.latestProtectedTokens() != null
                && source.latestProtectedTokens() > 0
                && source.modelConfigId() != null
                && source.modelConfigId().equals(source.latestUsageModelConfigId());
        int promptTokens = providerAnchored && source.latestInputTokens() != null
                ? source.latestInputTokens() : estimateTokens(source.contextUtf8Bytes());
        int completionTokens = providerAnchored && source.latestReservedOutputTokens() != null
                ? source.latestReservedOutputTokens()
                : source.maxOutputTokens() == null || source.maxOutputTokens() <= 0
                    ? 8_192 : source.maxOutputTokens();
        int usedTokens = providerAnchored ? source.latestProtectedTokens() : promptTokens + completionTokens;
        Integer maxContextTokens = source.maxContextTokens();
        String modelName = source.modelName();
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
                modelName,
                providerAnchored && source.latestUsageSource() != null
                        ? source.latestUsageSource() : "ESTIMATE",
                source.contextCapacitySource() == null ? "FALLBACK" : source.contextCapacitySource()
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

    private int estimateTokens(Long utf8Bytes) {
        if (utf8Bytes == null || utf8Bytes <= 0) return 0;
        return Math.toIntExact(Math.max(1L, (utf8Bytes + 2L) / 3L));
    }
}
