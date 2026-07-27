package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentRunLease;
import com.penmate.backend.domain.agent.run.model.AgentRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class AgentRunLeaseHeartbeatTest {

    @Test
    void renews_immediately_before_execution_is_allowed() {
        AgentRunLeaseService leases = mock(AgentRunLeaseService.class);
        AgentRunLease lease = lease();
        when(leases.renew(lease)).thenReturn(true);
        AgentRunLeaseHeartbeat heartbeat = new AgentRunLeaseHeartbeat(leases);

        try (var registration = heartbeat.start(lease)) {
            verify(leases).renew(lease);
        } finally {
            heartbeat.shutdown();
        }
    }

    @Test
    void rejects_execution_when_the_immediate_renewal_fails() {
        AgentRunLeaseService leases = mock(AgentRunLeaseService.class);
        AgentRunLease lease = lease();
        when(leases.renew(lease)).thenReturn(false);
        AgentRunLeaseHeartbeat heartbeat = new AgentRunLeaseHeartbeat(leases);

        try {
            assertThatThrownBy(() -> heartbeat.start(lease))
                    .isInstanceOf(AgentRunLeaseService.AgentRunLeaseLostException.class)
                    .hasMessageContaining("runId=70001");
        } finally {
            heartbeat.shutdown();
        }
    }

    @Test
    void records_a_failed_periodic_renewal_for_execution_boundaries() {
        AgentRunLeaseService leases = mock(AgentRunLeaseService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AgentRunLease lease = lease();
        when(leases.renew(lease)).thenReturn(true, false);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleAtFixedRate(task.capture(), eq(5L), eq(5L), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(ignored -> future);
        AgentRunLeaseHeartbeat heartbeat = new AgentRunLeaseHeartbeat(
                leases, scheduler, Duration.ofMillis(5));

        try (var registration = heartbeat.start(lease)) {
            task.getValue().run();
            assertThatThrownBy(registration::assertHealthy)
                    .isInstanceOf(AgentRunLeaseService.AgentRunLeaseLostException.class);
            assertThatThrownBy(() -> heartbeat.assertHealthy(lease.runId(), lease.executionToken()))
                    .isInstanceOf(AgentRunLeaseService.AgentRunLeaseLostException.class);
        } finally {
            heartbeat.shutdown();
        }

        verify(future).cancel(false);
        verify(scheduler).shutdownNow();
    }

    @Test
    void removes_the_active_registration_when_scheduling_fails() {
        AgentRunLeaseService leases = mock(AgentRunLeaseService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        AgentRunLease lease = lease();
        when(leases.renew(lease)).thenReturn(true);
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), eq(5L), eq(5L), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new IllegalStateException("scheduler unavailable"));
        AgentRunLeaseHeartbeat heartbeat = new AgentRunLeaseHeartbeat(
                leases, scheduler, Duration.ofMillis(5));

        try {
            assertThatThrownBy(() -> heartbeat.start(lease))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("scheduler unavailable");
            assertThatThrownBy(() -> heartbeat.start(lease))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("scheduler unavailable");
        } finally {
            heartbeat.shutdown();
        }

        verify(leases, times(2)).renew(lease);
    }

    private AgentRunLease lease() {
        return new AgentRunLease(70001L, "worker", 2L, 1,
                AgentRunStatus.PENDING, Instant.now().plusSeconds(60));
    }
}
