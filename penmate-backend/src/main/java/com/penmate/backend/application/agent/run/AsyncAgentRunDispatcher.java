package com.penmate.backend.application.agent.run;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncAgentRunDispatcher implements AgentRunDispatcher {

    private final AgentRunExecutor agentRunExecutor;

    @Async
    @Override
    public void dispatchInitialRun(Long runId, String traceId) {
        try {
            agentRunExecutor.execute(runId, traceId);
        } catch (Exception ex) {
            log.error("agent run dispatch failed: runId={}, traceId={}", runId, traceId, ex);
        }
    }
}
