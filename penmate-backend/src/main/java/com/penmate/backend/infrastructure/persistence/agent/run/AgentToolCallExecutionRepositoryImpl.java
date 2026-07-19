package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentToolCallExecution;
import com.penmate.backend.domain.agent.run.model.AgentToolCallExecutionStatus;
import com.penmate.backend.domain.agent.run.repository.AgentToolCallExecutionRepository;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.time.Instant;

@Repository
public class AgentToolCallExecutionRepositoryImpl implements AgentToolCallExecutionRepository {

    private final AgentToolCallExecutionMapper mapper;

    public AgentToolCallExecutionRepositoryImpl(AgentToolCallExecutionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AgentToolCallExecution find(Long runId, String toolCallId) {
        return mapper.find(runId, toolCallId);
    }

    @Override
    public boolean tryInsertStarted(AgentToolCallExecution execution) {
        try {
            return mapper.insertStarted(execution) == 1;
        } catch (RuntimeException failure) {
            if (isUniqueConflict(failure)) return false;
            throw failure;
        }
    }

    @Override
    public int markFinished(Long executionId, Long executionToken, AgentToolCallExecutionStatus status,
                            String resultJson, String errorCode, String errorMessage, Instant finishedAt) {
        if (status == null || !status.isTerminal()) {
            throw new IllegalArgumentException("Tool call execution target must be terminal");
        }
        return mapper.markFinished(executionId, executionToken, status.name(), resultJson,
                errorCode, errorMessage, finishedAt);
    }

    private boolean isUniqueConflict(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                String state = sqlException.getSQLState();
                if ("23000".equals(state) || "23505".equals(state)) return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
