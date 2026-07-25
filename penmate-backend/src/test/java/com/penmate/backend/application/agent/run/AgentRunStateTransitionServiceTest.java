package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRunLease;
import com.penmate.backend.domain.agent.run.model.AgentRunStatus;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunStateTransitionServiceTest {

    private final AgentRunLeaseService leases = mock(AgentRunLeaseService.class);
    private final AgentRunEventPublisher events = mock(AgentRunEventPublisher.class);
    private final AgentRunPendingApprovalRepository approvals = mock(AgentRunPendingApprovalRepository.class);
    private final AgentRunSuccessorService successors = mock(AgentRunSuccessorService.class);
    private final AgentRunStateTransitionService service =
            new AgentRunStateTransitionService(
                    leases, events, approvals, successors, mock(AgentRunOutputEventService.class));

    @Test
    void stale_worker_cannot_publish_completion_events() {
        AgentRunLease lease = lease();
        var lost = new AgentRunLeaseService.AgentRunLeaseLostException(lease.runId(), lease.executionToken());
        org.mockito.Mockito.doThrow(lost).when(leases).complete(lease);

        assertThatThrownBy(() -> service.completed(
                lease, "done", new LlmTokenUsage(1, 1, 2), false, null))
                .isSameAs(lost);

        verify(events, never()).publish(any(), any(), any());
    }

    @Test
    void completion_transitions_the_lease_before_publishing_message_and_terminal_events() {
        AgentRunLease lease = lease();
        when(events.publish(eq(lease.runId()), any(), any())).thenReturn(mock(AgentEvent.class));

        service.completed(lease, "done", new LlmTokenUsage(1, 1, 2), false, null);

        InOrder order = inOrder(leases, events);
        order.verify(leases).complete(lease);
        order.verify(events).publish(eq(lease.runId()), eq("message.completed"), any());
        order.verify(events).publish(eq(lease.runId()), eq("run.completed"), any());
        verify(events).publish(eq(lease.runId()), eq("message.completed"), argThat(payload -> {
            assertThat(payload).isInstanceOf(Map.class);
            return "final".equals(((Map<?, ?>) payload).get("channel"));
        }));
    }

    private AgentRunLease lease() {
        return new AgentRunLease(70001L, "worker", 2L, 1,
                AgentRunStatus.RUNNING, Instant.now().plus(1, java.time.temporal.ChronoUnit.MINUTES));
    }
}
