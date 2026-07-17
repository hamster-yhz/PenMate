package com.penmate.backend.domain.agent.run.repository;

import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.model.AgentRunLease;
import com.penmate.backend.domain.agent.run.model.AgentRunStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgentRunRepository {

    int insert(AgentRun run);

    int insertInput(AgentRunInput input);

    AgentRunInput findInput(Long runId);

    AgentRun findRun(Long runId);

    AgentRun findRunForUpdate(Long runId);

    AgentRun findSuccessor(Long predecessorRunId);

    Optional<AgentRunLease> tryAcquireLease(Long runId, String owner, LocalDateTime now, LocalDateTime leaseUntil);

    boolean renewLease(AgentRunLease lease, LocalDateTime leaseUntil);

    boolean ownsLease(AgentRunLease lease, LocalDateTime now);

    boolean ownsExecutionToken(Long runId, Long executionToken, LocalDateTime now);

    boolean transitionWithLease(AgentRunLease lease, AgentRunStatus target, String phase,
                                Long activeApprovalId, LocalDateTime nextRetryAt,
                                String errorCode, String errorMessage);

    boolean transitionExpected(Long runId, AgentRunStatus expected, AgentRunStatus target,
                               String phase, String errorCode, String errorMessage);

    boolean cancelRecoverable(Long runId, String errorCode, String errorMessage);

    int suspendExpiredRuns(LocalDateTime now, LocalDateTime nextRetryAt, int maxAttempts);

    List<Long> findClaimableRunIds(LocalDateTime now, int limit);
}
