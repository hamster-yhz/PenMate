package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.domain.agent.run.model.AgentRunInput;

public final class AgentToolTestContext {
    private AgentToolTestContext() {
    }

    public static AuthorizedAgentRunContext context(Long projectId, Long runId, Long sessionId,
                                                    Long turnId, Long ownerUserId, Long contextEpochId,
                                                    Long executionToken, Long chapterId, String traceId) {
        return new AuthorizedAgentRunContext(
                runId, projectId, sessionId, turnId, ownerUserId, contextEpochId, executionToken, traceId,
                new AgentRunInput(runId, "prompt", "CHAT", chapterId, null,
                        null, null, null, "hash"));
    }

    public static AuthorizedAgentRunContext context() {
        return context(9001L, 8001L, 7001L, 6001L, 1001L, 5001L, 1L, 3001L, "trace-1");
    }
}
