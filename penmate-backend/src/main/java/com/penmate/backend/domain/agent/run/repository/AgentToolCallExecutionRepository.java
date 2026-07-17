package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentToolCallExecution;
import com.penmate.backend.domain.agent.run.model.AgentToolCallExecutionStatus;

import java.time.LocalDateTime;

public interface AgentToolCallExecutionRepository {

    AgentToolCallExecution find(Long runId, String toolCallId);

    boolean tryInsertStarted(AgentToolCallExecution execution);

    int markFinished(Long executionId, Long executionToken, AgentToolCallExecutionStatus status,
                     String resultJson, String errorCode, String errorMessage, LocalDateTime finishedAt);
}
