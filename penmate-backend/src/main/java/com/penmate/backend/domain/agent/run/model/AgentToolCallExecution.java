package com.penmate.backend.domain.agent.run.model;

import java.time.Instant;
import java.util.Objects;

public record AgentToolCallExecution(
        Long executionId,
        Long runId,
        String toolCallId,
        String toolCode,
        String requestSha256,
        Long executionToken,
        String executionStatus,
        String resultJson,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
    public AgentToolCallExecution {
        executionId = Objects.requireNonNull(executionId, "executionId must not be null");
        runId = Objects.requireNonNull(runId, "runId must not be null");
        toolCallId = requireText(toolCallId, "toolCallId");
        toolCode = requireText(toolCode, "toolCode");
        requestSha256 = requireText(requestSha256, "requestSha256");
        if (!requestSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestSha256 must contain 64 hexadecimal characters");
        }
        executionToken = Objects.requireNonNull(executionToken, "executionToken must not be null");
        if (executionToken < 0) throw new IllegalArgumentException("executionToken must not be negative");
        AgentToolCallExecutionStatus status = AgentToolCallExecutionStatus.from(executionStatus);
        executionStatus = status.name();
        startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (status.isTerminal() && finishedAt == null) {
            throw new IllegalArgumentException("finishedAt is required for a terminal execution");
        }
        if (!status.isTerminal() && finishedAt != null) {
            throw new IllegalArgumentException("A started execution must not have finishedAt");
        }
    }

    public AgentToolCallExecutionStatus status() {
        return AgentToolCallExecutionStatus.from(executionStatus);
    }

    public static AgentToolCallExecution started(Long executionId, Long runId, String toolCallId,
                                                 String toolCode, String requestSha256,
                                                 Long executionToken, Instant startedAt) {
        return new AgentToolCallExecution(executionId, runId, toolCallId, toolCode, requestSha256,
                executionToken, AgentToolCallExecutionStatus.STARTED.name(), null, null, null,
                startedAt, null);
    }

    public boolean matches(String requestedToolCode, String requestedSha256) {
        return toolCode.equals(requestedToolCode) && requestSha256.equals(requestedSha256);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
