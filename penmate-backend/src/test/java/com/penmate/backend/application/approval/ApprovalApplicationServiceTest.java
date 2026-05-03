package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.AgentTaskStateMachine;
import com.penmate.backend.application.agent.ToolInvocationGateway;
import com.penmate.backend.application.agent.ToolInvocationGatewayResult;
import com.penmate.backend.application.agent.loop.AgentToolLoopController;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.ReviewApprovalCommand;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentTaskStateMachine taskStateMachine;

    @Mock
    private PendingToolInvocationRepository pendingToolInvocationRepository;

    @Mock
    private ToolInvocationGateway toolInvocationGateway;

    @Mock
    private AgentToolLoopController agentToolLoopController;

    @Mock
    private ApprovedToolInvocationAsyncResumer approvedToolInvocationAsyncResumer;

    @Mock
    private RealtimeEventService realtimeEventService;

    @InjectMocks
    private ApprovalApplicationService approvalApplicationService;

    @Test
    void UT_APP_APPROVAL_CREATE_SUCCESS() {
        when(approvalRequestRepository.insert(any())).thenAnswer(invocation -> {
            ApprovalRequest req = invocation.getArgument(0);
            req.setId(1L);
            req.setStatus("pending");
            return 1;
        });

        ApprovalRequest result = approvalApplicationService.create(
                new CreateApprovalCommand(1L, 2L, "UPDATE_CARD", "{}", 1, 1001L),
                "trace"
        );

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void UT_APP_APPROVAL_DETAIL_NOT_FOUND() {
        when(approvalRequestRepository.findById(1L)).thenReturn(null);

        assertThatThrownBy(() -> approvalApplicationService.detail(1L))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Approval request not found");
    }

    @Test
    void UT_APP_APPROVAL_APPROVE_NOT_PENDING() {
        when(approvalRequestRepository.approve(1L, 1001L, "ok")).thenReturn(0);

        assertThatThrownBy(() -> approvalApplicationService.approve(1L, new ReviewApprovalCommand(1001L, "ok"), "trace"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Approval is not in pending status or not found");
    }

    @Test
    void UT_APP_APPROVAL_APPROVE_SHOULD_LOAD_PENDING_INVOCATION_SNAPSHOT_INSTEAD_OF_REDISPATCHING_TASK() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(1L);
        request.setProjectId(9L);
        request.setTaskId(7L);
        when(approvalRequestRepository.approve(1L, 1001L, "ok")).thenReturn(1);
        when(approvalRequestRepository.findById(1L)).thenReturn(request);

        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                1L,
                9L,
                7L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9001}",
                "{}",
                1001L,
                "trace-1",
                "book-crud-delete-9001",
                "pending",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(pendingToolInvocationRepository.findByApprovalId(1L)).thenReturn(snapshot);
        when(pendingToolInvocationRepository.markStatus(1L, "pending", "executing")).thenReturn(1);

        approvalApplicationService.approve(1L, new ReviewApprovalCommand(1001L, "ok"), "trace-1");

        verify(pendingToolInvocationRepository).findByApprovalId(1L);
        verify(pendingToolInvocationRepository).markStatus(1L, "pending", "executing");
        verify(approvedToolInvocationAsyncResumer).resumeApprovedInvocation(request, snapshot);
        verifyNoInteractions(toolInvocationGateway);
    }

    @Test
    void UT_APP_APPROVAL_APPROVE_SHOULD_NOT_REPEAT_RESUME_WHEN_SNAPSHOT_ALREADY_CONSUMED() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(2L);
        request.setProjectId(9L);
        request.setTaskId(8L);
        when(approvalRequestRepository.approve(2L, 1001L, "ok")).thenReturn(1);
        when(approvalRequestRepository.findById(2L)).thenReturn(request);

        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                2L,
                9L,
                8L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9002}",
                "{}",
                1001L,
                "trace-2",
                "book-crud-delete-9002",
                "pending",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(pendingToolInvocationRepository.findByApprovalId(2L)).thenReturn(snapshot);
        when(pendingToolInvocationRepository.markStatus(2L, "pending", "executing")).thenReturn(0);

        approvalApplicationService.approve(2L, new ReviewApprovalCommand(1001L, "ok"), "trace-2");

        verify(pendingToolInvocationRepository).findByApprovalId(2L);
        verify(pendingToolInvocationRepository).markStatus(2L, "pending", "executing");
        verifyNoInteractions(approvedToolInvocationAsyncResumer);
        verify(agentRepository, never()).updateGenerationTaskStatus(9L, 8L, "running", null);
        verify(toolInvocationGateway, never()).resume(any());
    }

    @Test
    void UT_APP_APPROVAL_APPROVE_SHOULD_CLAIM_BEFORE_ASYNC_RESUME_AND_NOT_SWITCH_TASK_WHEN_CLAIM_FAILED() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(3L);
        request.setProjectId(9L);
        request.setTaskId(11L);
        when(approvalRequestRepository.approve(3L, 1001L, "ok")).thenReturn(1);
        when(approvalRequestRepository.findById(3L)).thenReturn(request);

        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                3L,
                9L,
                11L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9003}",
                "{}",
                1001L,
                "trace-3",
                "book-crud-delete-9003",
                "pending",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(pendingToolInvocationRepository.findByApprovalId(3L)).thenReturn(snapshot);
        when(pendingToolInvocationRepository.markStatus(3L, "pending", "executing")).thenReturn(0);

        approvalApplicationService.approve(3L, new ReviewApprovalCommand(1001L, "ok"), "trace-3");

        verify(pendingToolInvocationRepository).markStatus(3L, "pending", "executing");
        verifyNoInteractions(approvedToolInvocationAsyncResumer);
        verify(agentRepository, never()).findGenerationTask(9L, 11L);
        verify(agentRepository, never()).updateGenerationTaskStatus(9L, 11L, "running", null);
    }

    @Test
    void UT_APP_APPROVAL_APPROVE_SHOULD_RESUME_LOOP_WHEN_SNAPSHOT_RESUME_MODE_IS_RESUME_LOOP() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(8L);
        request.setProjectId(9L);
        request.setTaskId(16L);
        when(approvalRequestRepository.approve(8L, 1001L, "ok")).thenReturn(1);
        when(approvalRequestRepository.findById(8L)).thenReturn(request);

        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                8L,
                9L,
                16L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9008}",
                "{}",
                1001L,
                "trace-8",
                "book-crud-delete-9008",
                "pending",
                "loop-1",
                2,
                "call_8",
                "[{\"id\":\"call_8\"}]",
                "[{\"role\":\"user\",\"content\":\"delete project\"}]",
                "RESUME_LOOP",
                "{\"approvalType\":\"BOOK_DELETE\"}"
        );
        when(pendingToolInvocationRepository.findByApprovalId(8L)).thenReturn(snapshot);
        when(pendingToolInvocationRepository.markStatus(8L, "pending", "executing")).thenReturn(1);

        approvalApplicationService.approve(8L, new ReviewApprovalCommand(1001L, "ok"), "trace-8");

        verify(approvedToolInvocationAsyncResumer).resumeApprovedInvocation(request, snapshot);
        verify(toolInvocationGateway, never()).resume(any());
    }

    @Test
    void UT_APP_APPROVAL_ASYNC_RESUMER_SHOULD_MARK_TASK_RUNNING_THEN_COMPLETE_SNAPSHOT_WHEN_RESUME_SUCCEEDS() {
        ApprovedToolInvocationAsyncResumer resumer = new ApprovedToolInvocationAsyncResumer(
                agentRepository,
                taskStateMachine,
                pendingToolInvocationRepository,
                toolInvocationGateway,
                agentToolLoopController,
                realtimeEventService
        );
        ApprovalRequest request = new ApprovalRequest();
        request.setId(4L);
        request.setProjectId(9L);
        request.setTaskId(12L);

        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                4L,
                9L,
                12L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9004}",
                "{}",
                1001L,
                "trace-4",
                "book-crud-delete-9004",
                "executing",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(12L);
        task.setStatus("waiting_approval");
        when(agentRepository.findGenerationTask(9L, 12L)).thenReturn(task);
        when(taskStateMachine.parseStatus("waiting_approval")).thenReturn(AgentTaskStatus.WAITING_APPROVAL);
        when(pendingToolInvocationRepository.findByApprovalId(4L)).thenReturn(snapshot);
        doNothing().when(taskStateMachine).assertTransition("waiting_approval", AgentTaskStatus.RUNNING);
        when(agentRepository.updateGenerationTaskStatus(9L, 12L, "running", null)).thenReturn(1);
        when(pendingToolInvocationRepository.markStatus(4L, "executing", "completed")).thenReturn(1);
        when(toolInvocationGateway.resume(snapshot)).thenReturn(ToolInvocationGatewayResult.success("{\"result\":\"deleted\"}"));

        resumer.resumeApprovedInvocation(request, snapshot);

        verify(agentRepository).findGenerationTask(9L, 12L);
        verify(agentRepository).updateGenerationTaskStatus(9L, 12L, "running", null);
        verify(toolInvocationGateway).resume(snapshot);
        verify(pendingToolInvocationRepository).markStatus(4L, "executing", "completed");
    }

    @Test
    void UT_APP_APPROVAL_ASYNC_RESUMER_SHOULD_MARK_SNAPSHOT_AND_TASK_FAILED_WHEN_RESUME_FAILS() {
        ApprovedToolInvocationAsyncResumer resumer = new ApprovedToolInvocationAsyncResumer(
                agentRepository,
                taskStateMachine,
                pendingToolInvocationRepository,
                toolInvocationGateway,
                agentToolLoopController,
                realtimeEventService
        );
        ApprovalRequest request = new ApprovalRequest();
        request.setId(5L);
        request.setProjectId(9L);
        request.setTaskId(13L);

        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                5L,
                9L,
                13L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9005}",
                "{}",
                1001L,
                "trace-5",
                "book-crud-delete-9005",
                "executing",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(13L);
        task.setStatus("waiting_approval");
        AgentGenerationTask runningTask = new AgentGenerationTask();
        runningTask.setId(13L);
        runningTask.setStatus("running");
        when(agentRepository.findGenerationTask(9L, 13L)).thenReturn(task, runningTask);
        when(taskStateMachine.parseStatus("waiting_approval")).thenReturn(AgentTaskStatus.WAITING_APPROVAL);
        when(taskStateMachine.parseStatus("running")).thenReturn(AgentTaskStatus.RUNNING);
        when(pendingToolInvocationRepository.findByApprovalId(5L)).thenReturn(snapshot);
        doNothing().when(taskStateMachine).assertTransition("waiting_approval", AgentTaskStatus.RUNNING);
        doNothing().when(taskStateMachine).assertTransition("running", AgentTaskStatus.FAILED);
        when(agentRepository.updateGenerationTaskStatus(9L, 13L, "running", null)).thenReturn(1);
        when(agentRepository.updateGenerationTaskStatus(9L, 13L, "failed", "resume failed")).thenReturn(1);
        when(toolInvocationGateway.resume(snapshot)).thenReturn(new ToolInvocationGatewayResult(
                "FAILED",
                null,
                null,
                "RESUME_FAILED",
                "resume failed"
        ));
        when(pendingToolInvocationRepository.markStatus(5L, "executing", "failed")).thenReturn(1);

        resumer.resumeApprovedInvocation(request, snapshot);

        verify(agentRepository).updateGenerationTaskStatus(9L, 13L, "running", null);
        verify(toolInvocationGateway).resume(snapshot);
        verify(pendingToolInvocationRepository).markStatus(5L, "executing", "failed");
        verify(taskStateMachine).assertTransition("running", AgentTaskStatus.FAILED);
        verify(agentRepository).updateGenerationTaskStatus(9L, 13L, "failed", "resume failed");
        verify(realtimeEventService).publishGenerationFailed(9L, 13L, "RESUME_FAILED", "resume failed");
    }

    @Test
    void UT_APP_APPROVAL_ASYNC_RESUMER_SHOULD_NOT_OVERRIDE_TERMINAL_TASK_WHEN_RESUME_FAILS() {
        ApprovedToolInvocationAsyncResumer resumer = new ApprovedToolInvocationAsyncResumer(
                agentRepository,
                taskStateMachine,
                pendingToolInvocationRepository,
                toolInvocationGateway,
                agentToolLoopController,
                realtimeEventService
        );
        ApprovalRequest request = new ApprovalRequest();
        request.setId(6L);
        request.setProjectId(9L);
        request.setTaskId(14L);

        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                6L,
                9L,
                14L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9006}",
                "{}",
                1001L,
                "trace-6",
                "book-crud-delete-9006",
                "executing",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(14L);
        task.setStatus("done");
        when(agentRepository.findGenerationTask(9L, 14L)).thenReturn(task);
        when(taskStateMachine.parseStatus("done")).thenReturn(AgentTaskStatus.DONE);
        when(pendingToolInvocationRepository.findByApprovalId(6L)).thenReturn(snapshot);
        when(toolInvocationGateway.resume(snapshot)).thenReturn(new ToolInvocationGatewayResult(
                "FAILED",
                null,
                null,
                "RESUME_FAILED",
                "resume failed"
        ));
        when(pendingToolInvocationRepository.markStatus(6L, "executing", "failed")).thenReturn(1);

        resumer.resumeApprovedInvocation(request, snapshot);

        verify(pendingToolInvocationRepository).markStatus(6L, "executing", "failed");
        verify(agentRepository, never()).updateGenerationTaskStatus(9L, 14L, "failed", "resume failed");
    }

    @Test
    void UT_APP_APPROVAL_ASYNC_RESUMER_SHOULD_SKIP_WHEN_SNAPSHOT_NO_LONGER_EXECUTING() {
        ApprovedToolInvocationAsyncResumer resumer = new ApprovedToolInvocationAsyncResumer(
                agentRepository,
                taskStateMachine,
                pendingToolInvocationRepository,
                toolInvocationGateway,
                agentToolLoopController,
                realtimeEventService
        );
        ApprovalRequest request = new ApprovalRequest();
        request.setId(7L);
        request.setProjectId(9L);
        request.setTaskId(15L);

        PendingToolInvocationSnapshot staleSnapshot = new PendingToolInvocationSnapshot(
                7L,
                9L,
                15L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9007}",
                "{}",
                1001L,
                "trace-7",
                "book-crud-delete-9007",
                "failed",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        PendingToolInvocationSnapshot originalSnapshot = new PendingToolInvocationSnapshot(
                7L,
                9L,
                15L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9007}",
                "{}",
                1001L,
                "trace-7",
                "book-crud-delete-9007",
                "executing",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(pendingToolInvocationRepository.findByApprovalId(7L)).thenReturn(staleSnapshot);

        resumer.resumeApprovedInvocation(request, originalSnapshot);

        verifyNoInteractions(toolInvocationGateway);
        verify(agentRepository, never()).updateGenerationTaskStatus(9L, 15L, "running", null);
    }

    @Test
    void UT_APP_APPROVAL_REJECT_MARK_TASK_FAILED_SUCCESS() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(1L);
        request.setProjectId(9L);
        request.setTaskId(7L);
        when(approvalRequestRepository.reject(1L, 1001L, "no")).thenReturn(1);
        when(approvalRequestRepository.findById(1L)).thenReturn(request);

        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(7L);
        task.setStatus("waiting_approval");
        when(agentRepository.findGenerationTask(9L, 7L)).thenReturn(task);
        when(taskStateMachine.parseStatus("waiting_approval")).thenReturn(AgentTaskStatus.WAITING_APPROVAL);
        doNothing().when(taskStateMachine).assertTransition("waiting_approval", AgentTaskStatus.FAILED);
        when(agentRepository.updateGenerationTaskStatus(9L, 7L, "failed", "Approval rejected")).thenReturn(1);

        approvalApplicationService.reject(1L, new ReviewApprovalCommand(1001L, "no"), "trace-2");

        verify(realtimeEventService).publishGenerationFailed(9L, 7L, "AGENT_APPROVAL_REQUIRED", "Approval rejected");
    }

    @Test
    void UT_APP_APPROVAL_REJECT_SHOULD_PUBLISH_TOOL_CALL_FAILED_SEMANTICS_WHEN_LOOP_SNAPSHOT_EXISTS() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(9L);
        request.setProjectId(9L);
        request.setTaskId(17L);
        when(approvalRequestRepository.reject(9L, 1001L, "no")).thenReturn(1);
        when(approvalRequestRepository.findById(9L)).thenReturn(request);

        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                9L,
                9L,
                17L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9009}",
                "{}",
                1001L,
                "trace-9",
                "book-crud-delete-9009",
                "pending",
                "loop-9",
                2,
                "call_9",
                "[{\"id\":\"call_9\"}]",
                "[{\"role\":\"user\",\"content\":\"delete project\"}]",
                "RESUME_LOOP",
                "{\"approvalType\":\"BOOK_DELETE\"}"
        );
        when(pendingToolInvocationRepository.findByApprovalId(9L)).thenReturn(snapshot);

        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(17L);
        task.setStatus("waiting_approval");
        when(agentRepository.findGenerationTask(9L, 17L)).thenReturn(task);
        when(taskStateMachine.parseStatus("waiting_approval")).thenReturn(AgentTaskStatus.WAITING_APPROVAL);
        doNothing().when(taskStateMachine).assertTransition("waiting_approval", AgentTaskStatus.FAILED);
        when(agentRepository.updateGenerationTaskStatus(9L, 17L, "failed", "Approval rejected")).thenReturn(1);

        approvalApplicationService.reject(9L, new ReviewApprovalCommand(1001L, "no"), "trace-9");

        verify(realtimeEventService).publishProjectEvent(9L, "generation.tool_call", Map.of(
                "taskId", 17L,
                "toolCallId", "call_9",
                "status", "failed",
                "errorCode", "AGENT_APPROVAL_REJECTED",
                "errorMessage", "Approval rejected"
        ));
    }
}


