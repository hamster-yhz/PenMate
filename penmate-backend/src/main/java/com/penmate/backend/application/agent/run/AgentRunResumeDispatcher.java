package com.penmate.backend.application.agent.run;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunResumeDispatcher {

    private final AgentRunExecutor agentRunExecutor;
    private final AgentRunLeaseService leaseService;
    private final AgentRunEventPublisher eventPublisher;

    @Async
    public void dispatchResume(Long runId, String traceId) {
        dispatchRecovery(runId, traceId);
    }

    @Async
    public void dispatchRecovery(Long runId, String traceId) {
        var lease = leaseService.tryAcquire(runId).orElse(null);
        if (lease == null) return;
        try {
            agentRunExecutor.recover(runId, traceId, lease);
        } catch (Exception ex) {
            log.error("agent run resume dispatch failed: runId={}, traceId={}", runId, traceId, ex);
            try {
                var status = leaseService.handleFailure(lease, ex);
                eventPublisher.publish(runId,
                        status.name().equals("SUSPENDED") ? "run.suspended" : "run.failed",
                        java.util.Map.of("errorCode", "AGENT_RUN_RECOVERY_FAILED",
                                "errorMessage", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            } catch (Exception transitionEx) {
                log.error("agent run recovery state transition failed: runId={}, traceId={}", runId, traceId, transitionEx);
            }
        }
    }
}
