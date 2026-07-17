package com.penmate.backend.infrastructure.persistence.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.model.AgentRunLease;
import com.penmate.backend.domain.agent.run.model.AgentRunStatus;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AgentRunRepositoryImpl implements AgentRunRepository {

    private final AgentRunMapper agentRunMapper;

    public AgentRunRepositoryImpl(AgentRunMapper agentRunMapper) {
        this.agentRunMapper = agentRunMapper;
    }

    @Override
    public int insert(AgentRun run) {
        return agentRunMapper.insert(run);
    }

    @Override
    public int insertInput(AgentRunInput input) {
        return agentRunMapper.insertInput(input);
    }

    @Override
    public AgentRunInput findInput(Long runId) {
        return agentRunMapper.findInput(runId);
    }

    @Override
    public AgentRun findRun(Long runId) {
        return agentRunMapper.findRun(runId);
    }

    @Override
    public Optional<AgentRunLease> tryAcquireLease(Long runId, String owner,
                                                   LocalDateTime now, LocalDateTime leaseUntil) {
        AgentRun before = agentRunMapper.findRun(runId);
        if (before == null || agentRunMapper.acquireLease(runId, owner, now, leaseUntil) != 1) {
            return Optional.empty();
        }
        Map<String, Object> row = agentRunMapper.findLease(runId);
        return Optional.of(new AgentRunLease(
                runId,
                owner,
                longValue(row.get("executionToken")),
                intValue(row.get("attemptCount")),
                before.status(),
                localDateTime(row.get("expiresAt"))
        ));
    }

    @Override
    public boolean renewLease(AgentRunLease lease, LocalDateTime leaseUntil) {
        return agentRunMapper.renewLease(
                lease.runId(), lease.owner(), lease.executionToken(), leaseUntil) == 1;
    }

    @Override
    public boolean ownsLease(AgentRunLease lease, LocalDateTime now) {
        return agentRunMapper.ownsLease(
                lease.runId(), lease.owner(), lease.executionToken(), now) == 1;
    }

    @Override
    public boolean ownsExecutionToken(Long runId, Long executionToken, LocalDateTime now) {
        if (runId == null || executionToken == null || now == null) return false;
        return agentRunMapper.ownsExecutionToken(runId, executionToken, now) == 1;
    }

    @Override
    public boolean transitionWithLease(AgentRunLease lease, AgentRunStatus target, String phase,
                                       Long activeApprovalId, LocalDateTime nextRetryAt,
                                       String errorCode, String errorMessage) {
        if (!AgentRunStatus.RUNNING.canTransitionTo(target)) {
            throw new IllegalArgumentException("Invalid RUNNING transition to " + target);
        }
        return agentRunMapper.transitionWithLease(
                lease.runId(), lease.owner(), lease.executionToken(), target.name(), phase,
                activeApprovalId, nextRetryAt, errorCode, errorMessage, target.isTerminal()) == 1;
    }

    @Override
    public boolean transitionExpected(Long runId, AgentRunStatus expected, AgentRunStatus target,
                                      String phase, String errorCode, String errorMessage) {
        if (!expected.canTransitionTo(target)) {
            throw new IllegalArgumentException("Invalid " + expected + " transition to " + target);
        }
        return agentRunMapper.transitionExpected(runId, expected.name(), target.name(), phase,
                errorCode, errorMessage) == 1;
    }

    @Override
    public boolean cancelRecoverable(Long runId, String errorCode, String errorMessage) {
        return agentRunMapper.cancelRecoverable(runId, errorCode, errorMessage) == 1;
    }

    @Override
    public int suspendExpiredRuns(LocalDateTime now, LocalDateTime nextRetryAt, int maxAttempts) {
        return agentRunMapper.suspendExpiredRuns(now, nextRetryAt, maxAttempts);
    }

    @Override
    public List<Long> findClaimableRunIds(LocalDateTime now, int limit) {
        return agentRunMapper.findClaimableRunIds(now, limit);
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime time) return time;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }
}
