package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.run.model.AgentRunLease;
import com.penmate.backend.domain.agent.run.model.AgentRunStatus;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentRunLeaseService {

    static final int MAX_ATTEMPTS = 3;
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);

    private final AgentRunRepository runs;
    private final String workerId = UUID.randomUUID().toString();

    public AgentRunLeaseService(AgentRunRepository runs) {
        this.runs = runs;
    }

    public Optional<AgentRunLease> tryAcquire(Long runId) {
        Instant now = Instant.now();
        return runs.tryAcquireLease(runId, workerId, now, now.plus(LEASE_DURATION));
    }

    public void assertOwned(AgentRunLease lease) {
        if (!runs.ownsLease(lease, Instant.now())) {
            throw new AgentRunLeaseLostException(lease.runId(), lease.executionToken());
        }
    }

    public boolean renew(AgentRunLease lease) {
        return runs.renewLease(lease, Instant.now().plus(LEASE_DURATION));
    }

    public void waitingApproval(AgentRunLease lease, Long approvalId) {
        transition(lease, AgentRunStatus.WAITING_APPROVAL, "waiting_approval", approvalId, null, null, null);
    }

    public void complete(AgentRunLease lease) {
        transition(lease, AgentRunStatus.DONE, "completed", null, null, null, null);
    }

    public void supersede(AgentRunLease lease, String reason) {
        transition(lease, AgentRunStatus.SUPERSEDED, "superseded", null, null,
                "AGENT_RUN_DEPENDENCY_CHANGED", reason);
    }

    public void failTerminal(AgentRunLease lease, String errorCode, String errorMessage) {
        transition(lease, AgentRunStatus.FAILED, "failed", null, null, errorCode, errorMessage);
    }

    public boolean cancelWaitingApproval(Long runId, String errorCode, String errorMessage) {
        return runs.transitionExpected(runId, AgentRunStatus.WAITING_APPROVAL, AgentRunStatus.CANCELLED,
                "cancelled", errorCode, truncate(errorMessage, 500));
    }

    public AgentRunStatus handleFailure(AgentRunLease lease, Throwable failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        boolean terminal = failure instanceof IllegalArgumentException || failure instanceof BusinessException;
        AgentRunStatus target = terminal || lease.attemptCount() >= MAX_ATTEMPTS
                ? AgentRunStatus.FAILED
                : AgentRunStatus.SUSPENDED;
        Instant retryAt = target == AgentRunStatus.SUSPENDED
                ? Instant.now().plusSeconds(5L << Math.max(0, lease.attemptCount() - 1))
                : null;
        transition(lease, target, target == AgentRunStatus.SUSPENDED ? "suspended" : "failed",
                null, retryAt, errorCode(failure), message);
        return target;
    }

    private void transition(AgentRunLease lease, AgentRunStatus target, String phase,
                            Long approvalId, Instant retryAt, String errorCode, String errorMessage) {
        assertOwned(lease);
        if (!runs.transitionWithLease(lease, target, phase, approvalId, retryAt,
                errorCode, truncate(errorMessage, 500))) {
            throw new AgentRunLeaseLostException(lease.runId(), lease.executionToken());
        }
    }

    private String errorCode(Throwable failure) {
        if (failure instanceof BusinessException business) return business.getErrorCode();
        if (failure instanceof AgentRunLeaseLostException) return "AGENT_RUN_LEASE_LOST";
        return "AGENT_RUN_TRANSIENT_FAILURE";
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public static final class AgentRunLeaseLostException extends IllegalStateException {
        public AgentRunLeaseLostException(Long runId, Long executionToken) {
            super("Agent Run lease lost: runId=" + runId + ", executionToken=" + executionToken);
        }
    }
}
