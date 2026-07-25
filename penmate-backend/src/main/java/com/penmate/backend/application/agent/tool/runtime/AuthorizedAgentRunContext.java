package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.domain.agent.run.model.AgentRunInput;

import java.util.Objects;

public record AuthorizedAgentRunContext(
        Long runId,
        Long projectId,
        Long sessionId,
        Long turnId,
        Long ownerUserId,
        Long contextEpochId,
        Long executionToken,
        String traceId,
        AgentRunInput input
) {
    public AuthorizedAgentRunContext {
        runId = Objects.requireNonNull(runId, "runId must not be null");
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        turnId = Objects.requireNonNull(turnId, "turnId must not be null");
        ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        executionToken = Objects.requireNonNull(executionToken, "executionToken must not be null");
        traceId = traceId == null ? "" : traceId;
        input = Objects.requireNonNull(input, "input must not be null");
    }
}
