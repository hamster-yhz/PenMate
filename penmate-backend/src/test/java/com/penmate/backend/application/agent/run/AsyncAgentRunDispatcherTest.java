package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentRunLease;
import com.penmate.backend.domain.agent.run.model.AgentRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncAgentRunDispatcherTest {

    @Test
    void dispatchInitialRun_publishes_failed_event_when_executor_throws() {
        AgentRunExecutor executor = mock(AgentRunExecutor.class);
        AgentRunEventPublisher eventPublisher = mock(AgentRunEventPublisher.class);
        AgentRunLeaseService leaseService = mock(AgentRunLeaseService.class);
        AgentRunLease lease = new AgentRunLease(70001L, "worker", 1L, 3,
                AgentRunStatus.PENDING, Instant.now().plus(1, java.time.temporal.ChronoUnit.MINUTES));
        when(leaseService.tryAcquire(70001L)).thenReturn(Optional.of(lease));
        when(leaseService.handleFailure(eq(lease), any())).thenReturn(AgentRunStatus.FAILED);
        doThrow(new IllegalStateException("boom")).when(executor).execute(70001L, "trace-1", lease);

        AsyncAgentRunDispatcher dispatcher = new AsyncAgentRunDispatcher(
                executor, eventPublisher, leaseService, mock(AgentRunOutputEventService.class));

        dispatcher.dispatchInitialRun(70001L, "trace-1");

        verify(eventPublisher).publish(eq(70001L), eq("run.failed"), any(Map.class));
    }
}
