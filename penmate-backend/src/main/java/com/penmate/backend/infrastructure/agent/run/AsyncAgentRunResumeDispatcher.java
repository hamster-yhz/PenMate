package com.penmate.backend.infrastructure.agent.run;

import com.penmate.backend.application.agent.run.AgentRunRecoveryExecutionService;
import com.penmate.backend.application.agent.run.AgentRunResumeDispatcher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncAgentRunResumeDispatcher implements AgentRunResumeDispatcher {

    private final AgentRunRecoveryExecutionService executionService;

    public AsyncAgentRunResumeDispatcher(AgentRunRecoveryExecutionService executionService) {
        this.executionService = executionService;
    }

    @Async
    @Override
    public void dispatchResume(Long runId, String traceId) {
        executionService.execute(runId, traceId);
    }

    @Async
    @Override
    public void dispatchRecovery(Long runId, String traceId) {
        executionService.execute(runId, traceId);
    }
}
