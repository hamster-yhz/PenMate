package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import com.penmate.backend.domain.agent.service.AgentTaskTransitionPolicy;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovedToolInvocationAsyncResumerTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentTaskTransitionPolicy taskStateMachine;

    @Mock
    private PendingToolInvocationRepository pendingToolInvocationRepository;

    @Mock
    private ApprovalAgentResumeCoordinator approvalAgentResumeCoordinator;

    @Mock
    private RealtimeEventService realtimeEventService;

    @Test
    void UT_APP_APPROVAL_ASYNC_RESUMER_SHOULD_RESUME_LOOP_CONTROLLER_WHEN_SNAPSHOT_RESUME_MODE_IS_RESUME_LOOP() {
        ApprovedToolInvocationAsyncResumer resumer = new ApprovedToolInvocationAsyncResumer(
                agentRepository,
                taskStateMachine,
                pendingToolInvocationRepository,
                approvalAgentResumeCoordinator,
                realtimeEventService
        );
        ApprovalRequest request = new ApprovalRequest();
        request.setId(11L);
        request.setProjectId(9L);
        request.setTaskId(21L);

        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                11L,
                9L,
                21L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9011}",
                "{}",
                1001L,
                "trace-11",
                "book-crud-delete-9011",
                "executing",
                "loop-11",
                2,
                "call_11",
                "[{\"id\":\"call_11\"}]",
                "[{\"role\":\"user\",\"content\":\"delete project\"}]",
                "RESUME_LOOP",
                "{\"approvalType\":\"BOOK_DELETE\"}"
        );

        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(21L);
        task.setStatus("waiting_approval");
        when(agentRepository.findGenerationTask(9L, 21L)).thenReturn(task);
        when(taskStateMachine.parseStatus("waiting_approval")).thenReturn(AgentTaskStatus.WAITING_APPROVAL);
        when(pendingToolInvocationRepository.findByApprovalId(11L)).thenReturn(snapshot);
        doNothing().when(taskStateMachine).assertTransition("waiting_approval", AgentTaskStatus.RUNNING);
        when(agentRepository.updateGenerationTaskStatus(9L, 21L, "running", null)).thenReturn(1);
        when(approvalAgentResumeCoordinator.resumeApprovedInvocation(request, snapshot)).thenReturn(ToolCallResult.success("done"));
        when(pendingToolInvocationRepository.markStatus(11L, "executing", "completed")).thenReturn(1);

        resumer.resumeApprovedInvocation(request, snapshot);

        verify(agentRepository).updateGenerationTaskStatus(9L, 21L, "running", null);
        verify(approvalAgentResumeCoordinator).resumeApprovedInvocation(request, snapshot);
        verify(realtimeEventService, never()).publishGenerationFailed(9L, 21L, "RESUME_LOOP_FAILED", "done");
        verify(pendingToolInvocationRepository).markStatus(11L, "executing", "completed");
    }

    @Test
    void UT_APP_APPROVAL_ASYNC_RESUMER_SHOULD_MARK_SNAPSHOT_AND_TASK_FAILED_WHEN_RESUME_LOOP_FAILED() {
        ApprovedToolInvocationAsyncResumer resumer = new ApprovedToolInvocationAsyncResumer(
                agentRepository,
                taskStateMachine,
                pendingToolInvocationRepository,
                approvalAgentResumeCoordinator,
                realtimeEventService
        );
        ApprovalRequest request = new ApprovalRequest();
        request.setId(12L);
        request.setProjectId(9L);
        request.setTaskId(22L);

        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                12L,
                9L,
                22L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9012}",
                "{}",
                1001L,
                "trace-12",
                "book-crud-delete-9012",
                "executing",
                "loop-12",
                2,
                "call_12",
                "[{\"id\":\"call_12\"}]",
                "[{\"role\":\"user\",\"content\":\"delete project\"}]",
                "RESUME_LOOP",
                "{\"approvalType\":\"BOOK_DELETE\"}"
        );

        AgentGenerationTask waitingTask = new AgentGenerationTask();
        waitingTask.setId(22L);
        waitingTask.setStatus("waiting_approval");
        AgentGenerationTask runningTask = new AgentGenerationTask();
        runningTask.setId(22L);
        runningTask.setStatus("running");
        when(agentRepository.findGenerationTask(9L, 22L)).thenReturn(waitingTask, runningTask);
        when(taskStateMachine.parseStatus("waiting_approval")).thenReturn(AgentTaskStatus.WAITING_APPROVAL);
        when(taskStateMachine.parseStatus("running")).thenReturn(AgentTaskStatus.RUNNING);
        when(pendingToolInvocationRepository.findByApprovalId(12L)).thenReturn(snapshot);
        doNothing().when(taskStateMachine).assertTransition("waiting_approval", AgentTaskStatus.RUNNING);
        doNothing().when(taskStateMachine).assertTransition("running", AgentTaskStatus.FAILED);
        when(agentRepository.updateGenerationTaskStatus(9L, 22L, "running", null)).thenReturn(1);
        when(agentRepository.updateGenerationTaskStatus(9L, 22L, "failed", "loop resume failed")).thenReturn(1);
        when(pendingToolInvocationRepository.markStatus(12L, "executing", "failed")).thenReturn(1);
        when(approvalAgentResumeCoordinator.resumeApprovedInvocation(request, snapshot)).thenReturn(new ToolCallResult(
                "FAILED",
                null,
                null,
                "RESUME_LOOP_FAILED",
                "loop resume failed"
        ));

        resumer.resumeApprovedInvocation(request, snapshot);

        verify(approvalAgentResumeCoordinator).resumeApprovedInvocation(request, snapshot);
        verify(pendingToolInvocationRepository).markStatus(12L, "executing", "failed");
        verify(agentRepository).updateGenerationTaskStatus(9L, 22L, "failed", "loop resume failed");
        verify(realtimeEventService).publishGenerationFailed(9L, 22L, "RESUME_LOOP_FAILED", "loop resume failed");
    }
}
