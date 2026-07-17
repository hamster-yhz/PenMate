package com.penmate.backend.application.agent.tool.gateway;

import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalView;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalViewFactory;
import com.penmate.backend.application.agent.tool.definition.ToolExposure;
import com.penmate.backend.application.agent.tool.definition.ToolGovernancePolicy;
import com.penmate.backend.application.agent.tool.definition.ToolOperationPolicy;
import com.penmate.backend.application.agent.tool.definition.ToolPresentation;
import com.penmate.backend.application.agent.tool.handler.AgentToolHandler;
import com.penmate.backend.application.agent.tool.runtime.ToolCallExecutionService;
import com.penmate.backend.application.agent.tool.runtime.AgentToolMutationGuard;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import com.penmate.backend.application.approval.DefaultApprovalPolicyEngine;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.agent.run.repository.AgentToolCallExecutionRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.shared.model.ApprovalView;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolCallApplicationServiceTest {

    @Mock
    private AgentToolDefinitionSource toolDefinitionSource;

    @Mock
    private DefaultApprovalPolicyEngine approvalPolicyEngine;

    @Mock
    private ToolApprovalViewFactory toolApprovalViewFactory;

    @Mock
    private ApprovalApplicationService approvalApplicationService;

    @Mock
    private AgentRunPendingApprovalRepository pendingApprovalRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private RealtimeEventService realtimeEventService;

    @Mock
    private AgentToolHandler handler;

    @Mock
    private AgentToolCallExecutionRepository executionRepository;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

    @Mock
    private AgentToolMutationGuard mutationGuard;

    private ToolCallApplicationService toolCallApplicationService;

    private ToolCallExecutionService toolCallExecutionService;

    @BeforeEach
    void setUp() {
        toolCallExecutionService = new ToolCallExecutionService(List.of(handler), executionRepository,
                businessIdGenerator, mutationGuard, new ObjectMapper());
        lenient().when(businessIdGenerator.nextId()).thenReturn(99001L);
        lenient().when(executionRepository.tryInsertStarted(any())).thenReturn(true);
        lenient().when(executionRepository.markFinished(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        toolCallApplicationService = new ToolCallApplicationService(
                toolDefinitionSource,
                approvalPolicyEngine,
                toolApprovalViewFactory,
                approvalApplicationService,
                pendingApprovalRepository,
                toolCallExecutionService
        );
    }

    @Test
    void UT_APP_AGENT_TOOL_CALL_APPLICATION_SERVICE_SHOULD_BUILD_WAITING_APPROVAL_FROM_DESCRIPTOR_SINGLE_SOURCE_OF_TRUTH() {
        ToolCallRequest request = new ToolCallRequest(
                1L,
                11L,
                9L,
                501L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9001}",
                7L,
                "trace-approval",
                "{}",
                "idem-1",
                0,
                "call-1",
                "[{\"id\":\"call-1\"}]",
                "[{\"role\":\"user\"}]",
                "RESUME_LOOP",
                null,
                3L
        );
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "book_crud",
                new ToolPresentation("书籍 CRUD"),
                new ToolExposure(true, "书籍 CRUD；必须提供 operation", "{\"type\":\"object\"}"),
                new ToolGovernancePolicy(
                        new ApprovalPolicyDecision(false, ""),
                        2,
                        Map.of("delete", new ToolOperationPolicy("delete", new ApprovalPolicyDecision(true, "BOOK_DELETE")))
                )
        );
        ApprovalPolicyDecision decision = new ApprovalPolicyDecision(true, "BOOK_DELETE");
        ToolApprovalView approvalView = new ToolApprovalView(
                "book_crud",
                "书籍 CRUD",
                5,
                "BOOK_DELETE",
                "delete"
        );
        ApprovalRequest approvalRequest = new ApprovalRequest();
        approvalRequest.setId(99L);
        approvalRequest.setApprovalRequestId(88001L);

        when(toolDefinitionSource.getRequired("book_crud")).thenReturn(descriptor);
        when(handler.toolCode()).thenReturn("book_crud");
        when(approvalPolicyEngine.evaluate(descriptor, request)).thenReturn(decision);
        when(toolApprovalViewFactory.create(descriptor, decision)).thenReturn(approvalView);
        when(approvalApplicationService.create(any(CreateApprovalCommand.class), eq("trace-approval"))).thenReturn(approvalRequest);

        ToolCallResult result = toolCallApplicationService.executeToolCall(request);

        assertThat(result.status()).isEqualTo("WAITING_APPROVAL");
        assertThat(result.approvalId()).isEqualTo(88001L);

        ArgumentCaptor<CreateApprovalCommand> commandCaptor = ArgumentCaptor.forClass(CreateApprovalCommand.class);
        verify(approvalApplicationService).create(commandCaptor.capture(), eq("trace-approval"));
        assertThat(commandCaptor.getValue().approvalType()).isEqualTo("BOOK_DELETE");
        assertThat(commandCaptor.getValue().riskLevel()).isEqualTo(5);
        assertThat(commandCaptor.getValue().payloadJson()).isEqualTo(request.toolArgsJson());

        ArgumentCaptor<com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval> pendingCaptor =
                ArgumentCaptor.forClass(com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval.class);
        verify(pendingApprovalRepository).save(pendingCaptor.capture());
        assertThat(pendingCaptor.getValue().approvalId()).isEqualTo(88001L);
        assertThat(pendingCaptor.getValue().pendingApprovalId()).isEqualTo(88001L);
        verify(toolDefinitionSource).getRequired("book_crud");
        verify(toolApprovalViewFactory).create(descriptor, decision);
    }

    @Test
    void UT_APP_AGENT_TOOL_CALL_APPLICATION_SERVICE_SHOULD_RETURN_HANDLER_NOT_FOUND_WHEN_NO_MATCHED_HANDLER_EXISTS() {
        ToolCallRequest request = new ToolCallRequest(
                1L,
                11L,
                9L,
                501L,
                "missing_handler_tool",
                "{}",
                7L,
                "trace-missing-handler",
                "{}",
                "idem-missing",
                0,
                "call-missing",
                "[]",
                "[]",
                "RESUME_LOOP",
                null,
                3L
        );

        when(toolDefinitionSource.getRequired("missing_handler_tool")).thenReturn(new AgentToolDescriptor(
                "missing_handler_tool",
                new ToolPresentation("不存在的 handler"),
                new ToolExposure(true, "desc", "{}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
        ));
        when(approvalPolicyEngine.evaluate(org.mockito.ArgumentMatchers.any(), eq(request)))
                .thenReturn(new ApprovalPolicyDecision(false, ""));
 
        ToolCallResult result = toolCallApplicationService.executeToolCall(request);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TOOL_HANDLER_NOT_FOUND");
        verify(approvalApplicationService, never()).create(any(), any());
    }

    @Test
    void UT_APP_AGENT_TOOL_CALL_APPLICATION_SERVICE_SHOULD_RETURN_VALIDATION_FAILED_WHEN_HANDLER_VALIDATE_THROWS() {
        ToolCallRequest request = new ToolCallRequest(
                1L,
                11L,
                9L,
                501L,
                "context_enhancer",
                "{}",
                7L,
                "trace-validate",
                "{}",
                "idem-validate",
                0,
                "call-validate",
                "[]",
                "[]",
                "RESUME_LOOP",
                null,
                3L
        );
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "context_enhancer",
                new ToolPresentation("上下文增强"),
                new ToolExposure(true, "补充上下文", "{\"type\":\"object\"}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
        );

        when(handler.toolCode()).thenReturn("context_enhancer");
        when(toolDefinitionSource.getRequired("context_enhancer")).thenReturn(descriptor);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("prompt required"))
                .when(handler).validate(request);
        when(approvalPolicyEngine.evaluate(descriptor, request)).thenReturn(new ApprovalPolicyDecision(false, ""));
 
        ToolCallResult result = toolCallApplicationService.executeToolCall(request);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TOOL_VALIDATION_FAILED");
        assertThat(result.errorMessage()).isEqualTo("prompt required");
        verify(approvalApplicationService, never()).create(any(), any());
    }

    @Test
    void UT_APP_AGENT_TOOL_CALL_APPLICATION_SERVICE_SHOULD_EXECUTE_HANDLER_DIRECTLY_WHEN_APPROVAL_NOT_REQUIRED() {
        ToolCallRequest request = new ToolCallRequest(
                1L,
                11L,
                9L,
                501L,
                "context_enhancer",
                "{\"prompt\":\"hello\"}",
                7L,
                "trace-direct",
                "{}",
                "idem-2",
                0,
                "call-2",
                "[]",
                "[]",
                "RESUME_LOOP",
                null,
                3L
        );
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "context_enhancer",
                new ToolPresentation("上下文增强"),
                new ToolExposure(true, "补充上下文", "{\"type\":\"object\"}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
        );
        ToolCallResult success = ToolCallResult.success("{\"context\":\"ok\"}");

        when(handler.toolCode()).thenReturn("context_enhancer");
        when(toolDefinitionSource.getRequired("context_enhancer")).thenReturn(descriptor);
        when(approvalPolicyEngine.evaluate(descriptor, request)).thenReturn(new ApprovalPolicyDecision(false, ""));
        when(handler.execute(request)).thenReturn(success);

        ToolCallResult result = toolCallApplicationService.executeToolCall(request);

        assertThat(result).isSameAs(success);
        verify(handler).validate(request);
        verify(handler).execute(request);
        verify(approvalApplicationService, never()).create(any(), any());
    }
}
