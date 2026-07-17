package com.penmate.backend.application.agent.run;

public interface AgentRunDispatcher {

    void dispatchInitialRun(Long runId, String traceId);
}
