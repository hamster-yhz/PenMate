package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.llm.AgentLlmInvocationCancelledException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunInitialExecutionService {

    private final AgentRunExecutor agentRunExecutor;
    private final AgentRunEventPublisher eventPublisher;
    private final AgentRunLeaseService leaseService;
    private final AgentRunOutputEventService outputs;

    public void execute(Long runId, String traceId) {
        var lease = leaseService.tryAcquire(runId).orElse(null);
        if (lease == null) return;
        try {
            agentRunExecutor.execute(runId, traceId, lease);
        } catch (AgentLlmInvocationCancelledException exception) {
            log.info("agent run model invocation cancelled: runId={}, traceId={}", runId, traceId);
        } catch (Exception exception) {
            log.error("agent run dispatch failed: runId={}, traceId={}", runId, traceId, exception);
            try {
                var status = leaseService.handleFailure(lease, exception);
                if (status.name().equals("FAILED")) outputs.persistInterrupted(runId);
                eventPublisher.publish(runId, status.name().equals("SUSPENDED") ? "run.suspended" : "run.failed", Map.of(
                        "errorCode", status.name().equals("SUSPENDED")
                                ? "AGENT_RUN_TRANSIENT_FAILURE" : "AGENT_RUN_DISPATCH_FAILED",
                        "errorMessage", exception.getMessage() == null
                                ? exception.getClass().getSimpleName() : exception.getMessage()
                ));
            } catch (Exception publishException) {
                log.error("agent run failed event publish failed: runId={}, traceId={}",
                        runId, traceId, publishException);
            }
        }
    }
}
