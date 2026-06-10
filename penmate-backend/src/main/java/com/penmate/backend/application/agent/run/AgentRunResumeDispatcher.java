package com.penmate.backend.application.agent.run;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunResumeDispatcher {

    private final AgentRunExecutor agentRunExecutor;

    @Async
    public void dispatchResume(Long runId, String traceId) {
        try {
            agentRunExecutor.resume(runId, traceId);
        } catch (Exception ex) {
            log.error("agent run resume dispatch failed: runId={}, traceId={}", runId, traceId, ex);
        }
    }
}
