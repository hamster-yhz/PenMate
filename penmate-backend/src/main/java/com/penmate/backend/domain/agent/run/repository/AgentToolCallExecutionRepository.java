package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentToolCallExecution;
import com.penmate.backend.domain.agent.run.model.AgentToolCallExecutionStatus;

import java.time.Instant;
import java.util.List;

public interface AgentToolCallExecutionRepository {

    AgentToolCallExecution find(Long runId, String toolCallId);

    default List<AgentToolCallExecution> listByRun(Long runId) {
        return List.of();
    }

    boolean tryInsertStarted(AgentToolCallExecution execution);

    int markFinished(Long executionId, Long executionToken, AgentToolCallExecutionStatus status,
                     String resultJson, String errorCode, String errorMessage, Instant finishedAt);
}
