package com.penmate.backend.application.approval;

import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.ReviewApprovalCommand;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

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
        verify(auditService).write(eq("trace"), eq(1001L), eq("approval"), eq("create"), eq("agent_approval_requests"), eq("1"), eq("{}"), eq(201));
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
}


