package com.penmate.backend.application.agent.run;

public interface AgentRunResumeDispatcher {

    void dispatchResume(Long runId, String traceId);

    void dispatchRecovery(Long runId, String traceId);
}
