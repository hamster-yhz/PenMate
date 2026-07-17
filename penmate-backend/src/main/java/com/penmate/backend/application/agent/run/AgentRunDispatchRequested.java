package com.penmate.backend.application.agent.run;

import java.util.Objects;

public record AgentRunDispatchRequested(Long runId, String traceId) {

    public AgentRunDispatchRequested {
        Objects.requireNonNull(runId, "runId must not be null");
    }
}
