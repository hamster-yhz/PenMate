package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.run.AgentRunEventPublisher;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunPendingApprovalTimeoutGuardRunFlowTest {

    @Test
    void fails_stale_approved_or_resuming_run_pending_approvals() {
        AgentRunPendingApprovalRepository pendingApprovalRepository = mock(AgentRunPendingApprovalRepository.class);
        AgentRunEventPublisher eventPublisher = mock(AgentRunEventPublisher.class);
        AgentRunPendingApprovalTimeoutGuard guard = new AgentRunPendingApprovalTimeoutGuard(
                pendingApprovalRepository,
                eventPublisher
        );
        AgentRunPendingApproval approved = pendingApproval(88001L, 70001L, "APPROVED");
        AgentRunPendingApproval resuming = pendingApproval(88002L, 70002L, "RESUMING");

        when(pendingApprovalRepository.findStaleResumingOrApproved(10, 100))
                .thenReturn(List.of(approved, resuming));
        when(pendingApprovalRepository.markStatus(88001L, "APPROVED", "FAILED")).thenReturn(1);
        when(pendingApprovalRepository.markStatus(88002L, "RESUMING", "FAILED")).thenReturn(1);

        guard.failTimedOutResumingApprovals();

        verify(eventPublisher).publish(eq(70001L), eq("approval.expired"), any(Map.class));
        verify(eventPublisher).publish(eq(70001L), eq("run.failed"), any(Map.class));
        verify(eventPublisher).publish(eq(70002L), eq("approval.expired"), any(Map.class));
        verify(eventPublisher).publish(eq(70002L), eq("run.failed"), any(Map.class));
    }

    @Test
    void skips_event_publish_when_status_compare_and_set_loses_race() {
        AgentRunPendingApprovalRepository pendingApprovalRepository = mock(AgentRunPendingApprovalRepository.class);
        AgentRunEventPublisher eventPublisher = mock(AgentRunEventPublisher.class);
        AgentRunPendingApprovalTimeoutGuard guard = new AgentRunPendingApprovalTimeoutGuard(
                pendingApprovalRepository,
                eventPublisher
        );
        AgentRunPendingApproval approved = pendingApproval(88001L, 70001L, "APPROVED");

        when(pendingApprovalRepository.findStaleResumingOrApproved(10, 100))
                .thenReturn(List.of(approved));
        when(pendingApprovalRepository.markStatus(88001L, "APPROVED", "FAILED")).thenReturn(0);

        guard.failTimedOutResumingApprovals();

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    private AgentRunPendingApproval pendingApproval(Long approvalId, Long runId, String status) {
        return new AgentRunPendingApproval(
                1L,
                approvalId,
                approvalId,
                runId,
                9001L,
                9101L,
                9201L,
                "call-1",
                "story_bible_node_write",
                "{}",
                "{}",
                "{}",
                runId + ":1:call-1",
                status,
                201L,
                "trace-timeout",
                null,
                null
        );
    }
}
