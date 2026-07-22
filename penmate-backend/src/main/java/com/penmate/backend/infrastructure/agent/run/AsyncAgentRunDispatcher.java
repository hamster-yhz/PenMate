package com.penmate.backend.infrastructure.agent.run;

import com.penmate.backend.application.agent.run.AgentRunDispatcher;
import com.penmate.backend.application.agent.run.AgentRunInitialExecutionService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncAgentRunDispatcher implements AgentRunDispatcher {

    private final AgentRunInitialExecutionService executionService;

    public AsyncAgentRunDispatcher(AgentRunInitialExecutionService executionService) {
        this.executionService = executionService;
    }

    @Async
    @Override
    public void dispatchInitialRun(Long runId, String traceId) {
        executionService.execute(runId, traceId);
    }
}
