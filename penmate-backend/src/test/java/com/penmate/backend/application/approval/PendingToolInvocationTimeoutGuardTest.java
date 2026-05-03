package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.AgentTaskStateMachine;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingToolInvocationTimeoutGuardTest extends BaseApplicationServiceTest {

    @Mock
    private PendingToolInvocationRepository pendingToolInvocationRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentTaskStateMachine taskStateMachine;

    @Mock
    private RealtimeEventService realtimeEventService;

    @InjectMocks
    private PendingToolInvocationTimeoutGuard pendingToolInvocationTimeoutGuard;

    @Test
    void UT_APP_APPROVAL_TIMEOUT_GUARD_SHOULD_FAIL_STALE_EXECUTING_SNAPSHOT_AND_TASK() {
        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                41L,
                9L,
                21L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9041}",
                "{}",
                1001L,
                "trace-41",
                "book-crud-delete-9041",
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
        task.setId(21L);
        task.setStatus("waiting_approval");

        when(pendingToolInvocationRepository.findStaleExecutingSnapshots(10, 100)).thenReturn(List.of(snapshot));
        when(agentRepository.findGenerationTask(9L, 21L)).thenReturn(task);
        when(taskStateMachine.parseStatus("waiting_approval")).thenReturn(AgentTaskStatus.WAITING_APPROVAL);
        doNothing().when(taskStateMachine).assertTransition("waiting_approval", AgentTaskStatus.FAILED);
        when(pendingToolInvocationRepository.markStatus(41L, "executing", "failed")).thenReturn(1);
        when(agentRepository.updateGenerationTaskStatus(9L, 21L, "failed", "Pending tool invocation resume timed out")).thenReturn(1);

        pendingToolInvocationTimeoutGuard.failTimedOutExecutingSnapshots();

        verify(pendingToolInvocationRepository).findStaleExecutingSnapshots(10, 100);
        verify(pendingToolInvocationRepository).markStatus(41L, "executing", "failed");
        verify(agentRepository).updateGenerationTaskStatus(9L, 21L, "failed", "Pending tool invocation resume timed out");
        verify(realtimeEventService).publishGenerationFailed(9L, 21L, "AGENT_APPROVAL_RESUME_TIMEOUT", "Pending tool invocation resume timed out");
    }

    @Test
    void UT_APP_APPROVAL_TIMEOUT_GUARD_SHOULD_NOT_FAIL_RUNNING_TASK_THAT_IS_ALREADY_RESUMING() {
        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                42L,
                9L,
                22L,
                5L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9042}",
                "{}",
                1001L,
                "trace-42",
                "book-crud-delete-9042",
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
        task.setId(22L);
        task.setStatus("running");

        when(pendingToolInvocationRepository.findStaleExecutingSnapshots(10, 100)).thenReturn(List.of(snapshot));
        when(agentRepository.findGenerationTask(9L, 22L)).thenReturn(task);
        when(taskStateMachine.parseStatus("running")).thenReturn(AgentTaskStatus.RUNNING);

        pendingToolInvocationTimeoutGuard.failTimedOutExecutingSnapshots();

        verify(pendingToolInvocationRepository, never()).markStatus(42L, "executing", "failed");
        verify(agentRepository, never()).updateGenerationTaskStatus(9L, 22L, "failed", "Pending tool invocation resume timed out");
    }
}
