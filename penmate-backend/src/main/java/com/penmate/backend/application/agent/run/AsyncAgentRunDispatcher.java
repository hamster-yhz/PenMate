package com.penmate.backend.application.agent.run;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.penmate.backend.application.agent.llm.AgentLlmInvocationCancelledException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncAgentRunDispatcher implements AgentRunDispatcher {

    private final AgentRunExecutor agentRunExecutor;
    private final AgentRunEventPublisher eventPublisher;
    private final AgentRunLeaseService leaseService;
    private final AgentRunOutputEventService outputs;

    @Async
    @Override
    public void dispatchInitialRun(Long runId, String traceId) {
        var lease = leaseService.tryAcquire(runId).orElse(null);
        if (lease == null) return;
        try {
            agentRunExecutor.execute(runId, traceId, lease);
        } catch (AgentLlmInvocationCancelledException ex) {
            log.info("agent run model invocation cancelled: runId={}, traceId={}", runId, traceId);
        } catch (Exception ex) {
            log.error("agent run dispatch failed: runId={}, traceId={}", runId, traceId, ex);
            try {
                var status = leaseService.handleFailure(lease, ex);
                if (status.name().equals("FAILED")) outputs.persistInterrupted(runId);
                eventPublisher.publish(runId, status.name().equals("SUSPENDED") ? "run.suspended" : "run.failed", Map.of(
                        "errorCode", status.name().equals("SUSPENDED")
                                ? "AGENT_RUN_TRANSIENT_FAILURE" : "AGENT_RUN_DISPATCH_FAILED",
                        "errorMessage", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                ));
            } catch (Exception publishEx) {
                log.error("agent run failed event publish failed: runId={}, traceId={}", runId, traceId, publishEx);
            }
        }
    }
}
