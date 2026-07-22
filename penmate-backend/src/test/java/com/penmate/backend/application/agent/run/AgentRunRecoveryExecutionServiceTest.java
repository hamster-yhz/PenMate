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

class AgentRunRecoveryExecutionServiceTest {

    @Test
    void publishes_failed_event_when_recovery_throws() {
        AgentRunExecutor executor = mock(AgentRunExecutor.class);
        AgentRunLeaseService leases = mock(AgentRunLeaseService.class);
        AgentRunEventPublisher events = mock(AgentRunEventPublisher.class);
        AgentRunLease lease = new AgentRunLease(70002L, "worker", 2L, 3,
                AgentRunStatus.SUSPENDED, Instant.now().plusSeconds(60));
        when(leases.tryAcquire(70002L)).thenReturn(Optional.of(lease));
        when(leases.handleFailure(eq(lease), any())).thenReturn(AgentRunStatus.FAILED);
        doThrow(new IllegalStateException("boom")).when(executor).recover(70002L, "trace-2", lease);
        AgentRunRecoveryExecutionService service = new AgentRunRecoveryExecutionService(executor, leases, events);

        service.execute(70002L, "trace-2");

        verify(events).publish(eq(70002L), eq("run.failed"), any(Map.class));
    }
}
