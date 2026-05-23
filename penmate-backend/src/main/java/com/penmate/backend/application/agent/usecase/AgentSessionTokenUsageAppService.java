package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.query.AgentSessionTokenUsageQueryService;
import com.penmate.backend.application.agent.runtime.SessionTokenUsageView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Session token usage 查询用例。
 */
@Service
@Slf4j
public class AgentSessionTokenUsageAppService {

    private final AgentSessionTokenUsageQueryService agentSessionTokenUsageQueryService;

    public AgentSessionTokenUsageAppService(AgentSessionTokenUsageQueryService agentSessionTokenUsageQueryService) {
        this.agentSessionTokenUsageQueryService = agentSessionTokenUsageQueryService;
    }

    public SessionTokenUsageView getTokenUsage(Long projectId, Long sessionId, String traceId) {
        log.info("Agent session token usage requested: projectId={}, sessionId={}, traceId={}", projectId, sessionId, traceId);
        SessionTokenUsageView view = agentSessionTokenUsageQueryService.getTokenUsage(projectId, sessionId, traceId);
        log.info("Agent session token usage resolved: projectId={}, sessionId={}, traceId={}, usedTokens={}, maxContextTokens={}, modelName={}",
                projectId,
                sessionId,
                traceId,
                view == null ? null : view.usedTokens(),
                view == null ? null : view.maxContextTokens(),
                view == null ? null : view.modelName());
        return view;
    }
}
