package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalViewFactory;
import com.penmate.backend.application.agent.tool.definition.ToolExposure;
import com.penmate.backend.application.agent.tool.definition.ToolGovernancePolicy;
import com.penmate.backend.application.agent.tool.definition.ToolPresentation;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import com.penmate.backend.application.approval.DefaultApprovalPolicyEngine;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentToolGovernanceServiceTest {

    @Test
    void tool_request_uses_run_identity_without_task_id() {
        ToolCallRequest request = requestForRun(70001L, 90001L, 50001L, "todo_crud");

        assertThat(request.runId()).isEqualTo(70001L);
        assertThat(request.sessionId()).isEqualTo(90001L);
        assertThat(request.turnId()).isEqualTo(50001L);
        assertThat(Arrays.stream(ToolCallRequest.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("taskId");
    }

    @Test
    void returns_waiting_approval_without_mutating_old_task_tables() {
        AgentToolDefinitionSource toolDefinitionSource = mock(AgentToolDefinitionSource.class);
        DefaultApprovalPolicyEngine policyEngine = mock(DefaultApprovalPolicyEngine.class);
        ApprovalApplicationService approvalApplicationService = mock(ApprovalApplicationService.class);
        AgentRunPendingApprovalRepository pendingApprovalRepository = mock(AgentRunPendingApprovalRepository.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        ToolCallRequest request = requestForRun(70001L, 90001L, 50001L, "story_bible_update");
        ApprovalRequest approvalRequest = approvalRequest(88001L);
        when(toolDefinitionSource.getRequired("story_bible_update")).thenReturn(descriptor("story_bible_update"));
        when(policyEngine.evaluate(any(), eq(request))).thenReturn(new ApprovalPolicyDecision(true, "STORY_BIBLE_UPDATE"));
        when(approvalApplicationService.create(any(), eq("trace-1"))).thenReturn(approvalRequest);
        AgentToolGovernanceService service = new AgentToolGovernanceService(
                toolDefinitionSource,
                policyEngine,
                new ToolApprovalViewFactory(),
                approvalApplicationService,
                pendingApprovalRepository
        );

        AgentToolGovernanceDecision decision = service.beforeExecute(request);

        assertThat(decision.requiresApproval()).isTrue();
        assertThat(decision.approvalId()).isEqualTo(88001L);
        verify(pendingApprovalRepository).save(org.mockito.ArgumentMatchers.argThat(pending ->
                Long.valueOf(88001L).equals(pending.approvalId())
                        && Long.valueOf(88001L).equals(pending.pendingApprovalId())
        ));
        verifyNoInteractions(agentRepository);
    }

    private ToolCallRequest requestForRun(Long runId, Long sessionId, Long turnId, String toolCode) {
        return new ToolCallRequest(
                101L,
                runId,
                sessionId,
                turnId,
                toolCode,
                "{\"operation\":\"create\",\"sessionId\":" + sessionId + ",\"title\":\"Draft\",\"sourceType\":\"PLANNING\",\"todoStatus\":\"TODO\"}",
                201L,
                "trace-1",
                "{}",
                runId + ":1:call-1",
                1,
                "call-1",
                "[]",
                "[]",
                null,
                null
        );
    }

    private AgentToolDescriptor descriptor(String toolCode) {
        return new AgentToolDescriptor(
                toolCode,
                new ToolPresentation("Story Bible update"),
                new ToolExposure(true, "Update story bible", "{\"type\":\"object\"}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(true, "STORY_BIBLE_UPDATE"), 4, Map.of())
        );
    }

    private ApprovalRequest approvalRequest(Long id) {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(99L);
        request.setApprovalRequestId(id);
        request.setProjectId(101L);
        request.setStatus("pending");
        request.setApprovalType("STORY_BIBLE_UPDATE");
        return request;
    }
}
