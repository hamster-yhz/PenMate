package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunCancellationServiceTest {

    private final AgentRunRepository runs = mock(AgentRunRepository.class);
    private final AgentRunPendingApprovalRepository approvals = mock(AgentRunPendingApprovalRepository.class);
    private final AgentRunEventPublisher events = mock(AgentRunEventPublisher.class);
    private final AgentRunCancellationService service = new AgentRunCancellationService(runs, approvals, events);

    @Test
    void cancels_recoverable_run_invalidates_approval_and_publishes_once() {
        AgentRun running = run("RUNNING", "executing", 4L);
        AgentRun cancelled = run("CANCELLED", "cancelled", 5L);
        when(runs.findRun(70001L)).thenReturn(running, cancelled);
        when(runs.cancelRecoverable(70001L, "AGENT_RUN_CANCELLED", "Stop now")).thenReturn(true);

        AgentRun result = service.cancel(10001L, 70001L, 920001L, " Stop now ");

        assertThat(result.status().name()).isEqualTo("CANCELLED");
        InOrder order = inOrder(runs, approvals, events);
        order.verify(runs).cancelRecoverable(70001L, "AGENT_RUN_CANCELLED", "Stop now");
        order.verify(approvals).invalidateOpenByRunId(70001L);
        order.verify(events).publish(eq(70001L), eq("run.cancelled"), any());
    }

    @Test
    void repeated_cancel_is_idempotent_and_does_not_publish_another_event() {
        when(runs.findRun(70001L)).thenReturn(run("CANCELLED", "cancelled", 5L));

        AgentRun result = service.cancel(10001L, 70001L, 920001L, null);

        assertThat(result.status().name()).isEqualTo("CANCELLED");
        verify(runs, never()).cancelRecoverable(any(), any(), any());
        verify(events, never()).publish(any(), any(), any());
    }

    @Test
    void rejects_cancellation_by_another_user() {
        when(runs.findRun(70001L)).thenReturn(run("RUNNING", "executing", 4L));

        assertThatThrownBy(() -> service.cancel(10001L, 70001L, 7L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent Run belongs to another user");

        verify(runs, never()).cancelRecoverable(any(), any(), any());
    }

    @Test
    void rejects_non_cancelled_terminal_run() {
        when(runs.findRun(70001L)).thenReturn(run("DONE", "completed", 5L));

        assertThatThrownBy(() -> service.cancel(10001L, 70001L, 920001L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Terminal Agent Run cannot be cancelled");
    }

    private AgentRun run(String status, String phase, Long latestSequence) {
        return new AgentRun(70001L, 10001L, 20001L, 30001L, 920001L,
                status, phase, 99L, null, latestSequence, null, "trace-1", null, null);
    }
}
