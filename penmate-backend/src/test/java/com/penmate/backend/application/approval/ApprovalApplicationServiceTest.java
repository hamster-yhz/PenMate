package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.AgentOrchestrationDispatcher;
import com.penmate.backend.application.agent.AgentTaskStateMachine;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.ReviewApprovalCommand;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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
    private AgentOrchestrationDispatcher orchestrationDispatcher;

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
    void UT_APP_APPROVAL_APPROVE_RESUME_TASK_SUCCESS() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(1L);
        request.setProjectId(9L);
        request.setTaskId(7L);
        when(approvalRequestRepository.approve(1L, 1001L, "ok")).thenReturn(1);
        when(approvalRequestRepository.findById(1L)).thenReturn(request);

        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(7L);
        task.setStatus("waiting_approval");
        when(agentRepository.findGenerationTask(9L, 7L)).thenReturn(task);
        when(taskStateMachine.parseStatus("waiting_approval")).thenReturn(AgentTaskStatus.WAITING_APPROVAL);
        doNothing().when(taskStateMachine).assertTransition("waiting_approval", AgentTaskStatus.RUNNING);
        when(agentRepository.updateGenerationTaskStatus(9L, 7L, "running", null)).thenReturn(1);

        approvalApplicationService.approve(1L, new ReviewApprovalCommand(1001L, "ok"), "trace-1");

        verify(orchestrationDispatcher).dispatchAfterApproval(9L, 7L, "trace-1");
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
}


