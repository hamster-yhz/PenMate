package com.penmate.backend.application.agent.run;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunRecoveryExecutionService {

    private final AgentRunExecutor agentRunExecutor;
    private final AgentRunLeaseService leaseService;
    private final AgentRunEventPublisher eventPublisher;

    public void execute(Long runId, String traceId) {
        var lease = leaseService.tryAcquire(runId).orElse(null);
        if (lease == null) return;
        try {
            agentRunExecutor.recover(runId, traceId, lease);
        } catch (Exception exception) {
            log.error("agent run resume dispatch failed: runId={}, traceId={}", runId, traceId, exception);
            try {
                var status = leaseService.handleFailure(lease, exception);
                eventPublisher.publish(runId,
                        status.name().equals("SUSPENDED") ? "run.suspended" : "run.failed",
                        Map.of("errorCode", "AGENT_RUN_RECOVERY_FAILED",
                                "errorMessage", exception.getMessage() == null
                                        ? exception.getClass().getSimpleName() : exception.getMessage()));
            } catch (Exception transitionException) {
                log.error("agent run recovery state transition failed: runId={}, traceId={}",
                        runId, traceId, transitionException);
            }
        }
    }
}
