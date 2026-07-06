package com.penmate.backend.application.agent.run;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncAgentRunDispatcher implements AgentRunDispatcher {

    private final AgentRunExecutor agentRunExecutor;
    private final AgentRunEventPublisher eventPublisher;

    @Async
    @Override
    public void dispatchInitialRun(Long runId, String traceId) {
        try {
            agentRunExecutor.execute(runId, traceId);
        } catch (Exception ex) {
            log.error("agent run dispatch failed: runId={}, traceId={}", runId, traceId, ex);
            try {
                eventPublisher.publish(runId, "run.failed", Map.of(
                        "errorCode", "AGENT_RUN_DISPATCH_FAILED",
                        "errorMessage", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                ));
            } catch (Exception publishEx) {
                log.error("agent run failed event publish failed: runId={}, traceId={}", runId, traceId, publishEx);
            }
        }
    }
}
