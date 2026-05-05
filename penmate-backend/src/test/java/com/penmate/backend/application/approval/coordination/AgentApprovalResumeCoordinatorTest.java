package com.penmate.backend.application.approval.coordination;

import com.penmate.backend.application.agent.tool.runtime.ToolCallResumeService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentApprovalResumeCoordinatorTest {

    @Mock
    private ToolCallResumeService toolCallResumeService;

    @Test
    void UT_APP_APPROVAL_RESUME_COORDINATOR_SHOULD_DELEGATE_APPROVED_INVOCATION_TO_TOOL_CALL_RESUME_SERVICE() {
        AgentApprovalResumeCoordinator coordinator = new AgentApprovalResumeCoordinator(toolCallResumeService);
        ApprovalRequest request = new ApprovalRequest();
        request.setId(11L);
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
        ToolCallResult expected = ToolCallResult.success("done");
        when(toolCallResumeService.resumeFromPending(request, snapshot)).thenReturn(expected);

        ToolCallResult result = coordinator.resumeApprovedInvocation(request, snapshot);

        assertThat(result).isSameAs(expected);
        verify(toolCallResumeService).resumeFromPending(request, snapshot);
    }
}
