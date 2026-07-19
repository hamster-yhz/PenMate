package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunStatus;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class AgentRunReconciler {

    private static final int BATCH_SIZE = 100;

    private final AgentRunRepository runs;
    private final AgentRunDispatcher initialDispatcher;
    private final AgentRunResumeDispatcher recoveryDispatcher;

    public AgentRunReconciler(AgentRunRepository runs,
                              AgentRunDispatcher initialDispatcher,
                              AgentRunResumeDispatcher recoveryDispatcher) {
        this.runs = runs;
        this.initialDispatcher = initialDispatcher;
        this.recoveryDispatcher = recoveryDispatcher;
    }

    @Scheduled(fixedDelayString = "${penmate.agent.reconcile-delay:PT30S}")
    public void reconcile() {
        Instant now = Instant.now();
        int expired = runs.suspendExpiredRuns(now, now.plusSeconds(5), AgentRunLeaseService.MAX_ATTEMPTS);
        if (expired > 0) log.warn("agent.run.leases.expired: count={}", expired);

        for (Long runId : runs.findClaimableRunIds(now, BATCH_SIZE)) {
            AgentRun run = runs.findRun(runId);
            if (run == null) continue;
            if (run.status() == AgentRunStatus.PENDING) {
                initialDispatcher.dispatchInitialRun(runId, run.traceId());
            } else {
                recoveryDispatcher.dispatchRecovery(runId, run.traceId());
            }
        }
    }
}
