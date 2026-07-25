package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;

import java.util.List;
import java.util.Objects;

public record AgentRunLoopRequest(
        Long runId,
        Long projectId,
        Long sessionId,
        Long turnId,
        String traceId,
        List<AgentLlmMessage> messages,
        List<AgentLlmToolSchema> toolSchemas,
        AgentLlmExecutionConfig executionConfig,
        Long operatorId,
        Long executionToken
) {

    public AgentRunLoopRequest {
        runId = Objects.requireNonNull(runId, "runId must not be null");
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        turnId = Objects.requireNonNull(turnId, "turnId must not be null");
        traceId = traceId == null ? "" : traceId.trim();
        messages = List.copyOf(messages == null ? List.of() : messages);
        toolSchemas = List.copyOf(toolSchemas == null ? List.of() : toolSchemas);
        executionConfig = executionConfig == null ? AgentLlmExecutionConfig.builder().build() : executionConfig;
        executionToken = Objects.requireNonNull(executionToken, "executionToken must not be null");
    }
}
