package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.run.AgentRunEventPublisher;
import com.penmate.backend.application.agent.run.AgentRunResumeDispatcher;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.ReviewApprovalCommand;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalApplicationServiceRunFlowTest {

    @Test
    void approval_approved_emits_run_event_and_dispatches_run_resume_once() {
        ApprovalRequestRepository approvalRequestRepository = mock(ApprovalRequestRepository.class);
        AgentRunPendingApprovalRepository pendingApprovalRepository = mock(AgentRunPendingApprovalRepository.class);
        AgentRunEventPublisher eventPublisher = mock(AgentRunEventPublisher.class);
        AgentRunResumeDispatcher runResumeDispatcher = mock(AgentRunResumeDispatcher.class);
        ApprovalApplicationService service = new ApprovalApplicationService(
                approvalRequestRepository,
                pendingApprovalRepository,
                eventPublisher,
                runResumeDispatcher
        );
        ApprovalRequest approval = approvalRequest(88001L, 70001L);
        AgentRunPendingApproval pending = pendingApproval(88001L, 70001L);

        when(approvalRequestRepository.approveByApprovalRequestId(88001L, 201L, "ok")).thenReturn(1);
        when(approvalRequestRepository.findByApprovalRequestId(88001L)).thenReturn(approval);
        when(pendingApprovalRepository.findByApprovalId(88001L)).thenReturn(pending);
        when(pendingApprovalRepository.markStatus(88001L, "PENDING", "APPROVED")).thenReturn(1);

        service.approve(88001L, new ReviewApprovalCommand(201L, "ok"), "trace-1");

        verify(eventPublisher).publish(eq(70001L), eq("approval.approved"), any(Map.class));
        verify(runResumeDispatcher).dispatchResume(70001L, "trace-1");
    }

    @Test
    void duplicate_approval_does_not_publish_or_dispatch_resume_again() {
        ApprovalRequestRepository approvalRequestRepository = mock(ApprovalRequestRepository.class);
        AgentRunPendingApprovalRepository pendingApprovalRepository = mock(AgentRunPendingApprovalRepository.class);
        AgentRunEventPublisher eventPublisher = mock(AgentRunEventPublisher.class);
        AgentRunResumeDispatcher runResumeDispatcher = mock(AgentRunResumeDispatcher.class);
        ApprovalApplicationService service = new ApprovalApplicationService(
                approvalRequestRepository,
                pendingApprovalRepository,
                eventPublisher,
                runResumeDispatcher
        );
        ApprovalRequest approval = approvalRequest(88001L, 70001L);
        approval.setStatus("approved");

        when(approvalRequestRepository.approveByApprovalRequestId(88001L, 201L, "ok")).thenReturn(0);
        when(approvalRequestRepository.findByApprovalRequestId(88001L)).thenReturn(approval);

        service.approve(88001L, new ReviewApprovalCommand(201L, "ok"), "trace-1");

        verify(eventPublisher, never()).publish(eq(70001L), eq("approval.approved"), any());
        verify(runResumeDispatcher, never()).dispatchResume(any(), any());
    }

    @Test
    void create_approval_uses_run_id_not_task_id() {
        ApprovalRequestRepository approvalRequestRepository = mock(ApprovalRequestRepository.class);
        AgentRunPendingApprovalRepository pendingApprovalRepository = mock(AgentRunPendingApprovalRepository.class);
        AgentRunEventPublisher eventPublisher = mock(AgentRunEventPublisher.class);
        AgentRunResumeDispatcher runResumeDispatcher = mock(AgentRunResumeDispatcher.class);
        ApprovalApplicationService service = new ApprovalApplicationService(
                approvalRequestRepository,
                pendingApprovalRepository,
                eventPublisher,
                runResumeDispatcher
        );

        when(approvalRequestRepository.insert(any())).thenAnswer(invocation -> {
            ApprovalRequest request = invocation.getArgument(0);
            request.setId(1L);
            request.setApprovalRequestId(88001L);
            request.setStatus("pending");
            return 1;
        });

        service.create(new CreateApprovalCommand(9001L, 70001L, "STORY_BIBLE_UPDATE", "{}", 4, 201L), "trace-create");

        verify(approvalRequestRepository).insert(org.mockito.ArgumentMatchers.argThat(request ->
                Long.valueOf(70001L).equals(request.getRunId())
        ));
    }

    private ApprovalRequest approvalRequest(Long approvalId, Long runId) {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(approvalId);
        request.setApprovalRequestId(approvalId);
        request.setProjectId(9001L);
        request.setRunId(runId);
        request.setApprovalType("STORY_BIBLE_UPDATE");
        request.setStatus("pending");
        return request;
    }

    private AgentRunPendingApproval pendingApproval(Long approvalId, Long runId) {
        return new AgentRunPendingApproval(
                1L,
                approvalId,
                approvalId,
                runId,
                9001L,
                9101L,
                9201L,
                "call-1",
                "story_bible_update",
                "{}",
                "{}",
                "{}",
                "70001:1:call-1",
                "PENDING",
                201L,
                "trace-1",
                null,
                null
        );
    }
}
