package com.penmate.backend.application.agent.tool.gateway;

import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalView;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalViewFactory;
import com.penmate.backend.application.agent.tool.definition.ToolExposure;
import com.penmate.backend.application.agent.tool.definition.ToolGovernancePolicy;
import com.penmate.backend.application.agent.tool.definition.ToolLifecycleStatus;
import com.penmate.backend.application.agent.tool.definition.ToolOperationPolicy;
import com.penmate.backend.application.agent.tool.definition.ToolPresentation;
import com.penmate.backend.application.agent.tool.runtime.ToolCallExecutionService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.agent.tool.runtime.ToolApprovalPreview;
import com.penmate.backend.application.agent.tool.runtime.AgentRunExecutionContextResolver;
import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import com.penmate.backend.application.approval.DefaultApprovalPolicyEngine;
import com.penmate.backend.application.approval.command.CreateApprovalCommand;
import com.penmate.backend.application.approval.command.CreateToolApprovalCommand;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.shared.model.ApprovalView;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
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

    private ToolCallApplicationService toolCallApplicationService;

    private ToolCallExecutionService toolCallExecutionService;

    @Mock
    private AgentRunExecutionContextResolver executionContexts;

    @BeforeEach
    void setUp() {
        toolCallExecutionService = org.mockito.Mockito.mock(ToolCallExecutionService.class);
        toolCallApplicationService = new ToolCallApplicationService(
                toolDefinitionSource,
                approvalPolicyEngine,
                toolApprovalViewFactory,
                approvalApplicationService,
                pendingApprovalRepository,
                toolCallExecutionService,
                new ToolApprovalPreview(new JacksonJsonCodec(new ObjectMapper()), List.of()),
                new JacksonJsonCodec(new ObjectMapper()),
                executionContexts
        );
        when(executionContexts.resolve(any())).thenAnswer(invocation ->
                context(invocation.getArgument(0)));
    }

    @Test
    void UT_APP_AGENT_TOOL_CALL_APPLICATION_SERVICE_SHOULD_BUILD_WAITING_APPROVAL_FROM_DESCRIPTOR_SINGLE_SOURCE_OF_TRUTH() {
        ToolCallRequest request = new ToolCallRequest(11L, "book_crud", "{\"operation\":\"delete\"}",
                "idem-1", 0, "call-1", "{}", "[{\"id\":\"call-1\"}]",
                "[{\"role\":\"user\"}]", "RESUME_LOOP", null, 3L);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "book_crud",
                new ToolPresentation("书籍 CRUD"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE, "书籍 CRUD；必须提供 operation", "{\"type\":\"object\"}"),
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
        when(approvalPolicyEngine.evaluate(descriptor, request, "STANDARD")).thenReturn(decision);
        when(toolApprovalViewFactory.create(descriptor, decision)).thenReturn(approvalView);
        when(approvalApplicationService.createForTool(any(CreateApprovalCommand.class),
                any(CreateToolApprovalCommand.class), eq("trace-approval"))).thenReturn(approvalRequest);

        ToolCallResult result = toolCallApplicationService.executeToolCall(request);

        assertThat(result.status()).isEqualTo("WAITING_APPROVAL");
        assertThat(result.approvalId()).isEqualTo(88001L);

        ArgumentCaptor<CreateApprovalCommand> commandCaptor = ArgumentCaptor.forClass(CreateApprovalCommand.class);
        ArgumentCaptor<CreateToolApprovalCommand> toolCommandCaptor =
                ArgumentCaptor.forClass(CreateToolApprovalCommand.class);
        verify(approvalApplicationService).createForTool(
                commandCaptor.capture(), toolCommandCaptor.capture(), eq("trace-approval"));
        assertThat(commandCaptor.getValue().approvalType()).isEqualTo("BOOK_DELETE");
        assertThat(commandCaptor.getValue().riskLevel()).isEqualTo(5);
        assertThat(commandCaptor.getValue().payloadJson()).isEqualTo(request.toolArgsJson());

        assertThat(toolCommandCaptor.getValue().approvalBindingJson())
                .contains("book_crud", "toolArgsHash", "contextEpochId", "safetyMode", "expectedState");
        verify(toolDefinitionSource).getRequired("book_crud");
        verify(toolApprovalViewFactory).create(descriptor, decision);
    }

    @Test
    void replays_existing_pending_approval_after_crash_without_creating_another_approval() {
        ToolCallRequest request = new ToolCallRequest(11L, "book_crud", "{\"operation\":\"delete\"}",
                "11:call-recovery", 1, "call-recovery", "{}", "[]", "[]", null, null, 3L);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "book_crud", new ToolPresentation("Book CRUD"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE, "desc", "{}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(true, "BOOK_DELETE"), 5, Map.of()));
        var pending = new com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval(
                1L, 88001L, 88001L, 11L, 1L, 9L, 501L, "call-recovery", "book_crud",
                request.toolArgsJson(), "{}", "[]", request.idempotencyKey(), "PENDING",
                7L, "trace-original", null, null);
        when(toolDefinitionSource.getRequired("book_crud")).thenReturn(descriptor);
        when(approvalPolicyEngine.evaluate(descriptor, request, "STANDARD")).thenReturn(
                new ApprovalPolicyDecision(true, "BOOK_DELETE"));
        when(pendingApprovalRepository.findByIdempotencyKey(request.idempotencyKey())).thenReturn(pending);

        ToolCallResult result = toolCallApplicationService.executeToolCall(request);

        assertThat(result.status()).isEqualTo("WAITING_APPROVAL");
        assertThat(result.approvalId()).isEqualTo(88001L);
        assertThat(result.approvalPreview()).containsEntry("operation", "delete");
        verify(approvalApplicationService, never()).create(any(), any());
        verify(pendingApprovalRepository, never()).save(any());
    }

    @Test
    void UT_APP_AGENT_TOOL_CALL_APPLICATION_SERVICE_SHOULD_RETURN_HANDLER_NOT_FOUND_WHEN_NO_MATCHED_HANDLER_EXISTS() {
        ToolCallRequest request = new ToolCallRequest(11L, "missing_handler_tool", "{}",
                "idem-missing", 0, "call-missing", "{}", "[]", "[]",
                "RESUME_LOOP", null, 3L);

        when(toolDefinitionSource.getRequired("missing_handler_tool")).thenReturn(new AgentToolDescriptor(
                "missing_handler_tool",
                new ToolPresentation("不存在的 handler"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE, "desc", "{}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
        ));
        when(approvalPolicyEngine.evaluate(org.mockito.ArgumentMatchers.any(), eq(request), eq("STANDARD")))
                .thenReturn(new ApprovalPolicyDecision(false, ""));
        when(toolCallExecutionService.execute(context(request), request)).thenReturn(
                ToolCallResult.failed("TOOL_HANDLER_NOT_FOUND", "Tool handler not found: missing_handler_tool"));
 
        ToolCallResult result = toolCallApplicationService.executeToolCall(request);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TOOL_HANDLER_NOT_FOUND");
        verify(approvalApplicationService, never()).create(any(), any());
    }

    @Test
    void UT_APP_AGENT_TOOL_CALL_APPLICATION_SERVICE_SHOULD_RETURN_VALIDATION_FAILED_WHEN_HANDLER_VALIDATE_THROWS() {
        ToolCallRequest request = new ToolCallRequest(11L, "custom_tool", "{}",
                "idem-validate", 0, "call-validate", "{}", "[]", "[]",
                "RESUME_LOOP", null, 3L);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "custom_tool",
                new ToolPresentation("上下文增强"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE, "补充上下文", "{\"type\":\"object\"}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
        );

        when(toolDefinitionSource.getRequired("custom_tool")).thenReturn(descriptor);
        when(approvalPolicyEngine.evaluate(descriptor, request, "STANDARD")).thenReturn(new ApprovalPolicyDecision(false, ""));
        when(toolCallExecutionService.execute(context(request), request)).thenReturn(
                ToolCallResult.failed("TOOL_VALIDATION_FAILED", "prompt required"));
 
        ToolCallResult result = toolCallApplicationService.executeToolCall(request);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TOOL_VALIDATION_FAILED");
        assertThat(result.errorMessage()).isEqualTo("prompt required");
        verify(approvalApplicationService, never()).create(any(), any());
    }

    @Test
    void UT_APP_AGENT_TOOL_CALL_APPLICATION_SERVICE_SHOULD_EXECUTE_HANDLER_DIRECTLY_WHEN_APPROVAL_NOT_REQUIRED() {
        ToolCallRequest request = new ToolCallRequest(11L, "custom_tool", "{\"prompt\":\"hello\"}",
                "idem-2", 0, "call-2", "{}", "[]", "[]",
                "RESUME_LOOP", null, 3L);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "custom_tool",
                new ToolPresentation("上下文增强"),
                new ToolExposure(ToolLifecycleStatus.ACTIVE, "补充上下文", "{\"type\":\"object\"}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 1, Map.of())
        );
        ToolCallResult success = ToolCallResult.success("{\"context\":\"ok\"}");

        when(toolDefinitionSource.getRequired("custom_tool")).thenReturn(descriptor);
        when(approvalPolicyEngine.evaluate(descriptor, request, "STANDARD")).thenReturn(new ApprovalPolicyDecision(false, ""));
        when(toolCallExecutionService.execute(context(request), request)).thenReturn(success);

        ToolCallResult result = toolCallApplicationService.executeToolCall(request);

        assertThat(result).isSameAs(success);
        verify(toolCallExecutionService).execute(context(request), request);
        verify(approvalApplicationService, never()).create(any(), any());
    }

    @Test
    void rejects_an_approved_call_when_its_immutable_binding_does_not_match() {
        ToolCallRequest request = new ToolCallRequest(11L, "custom_tool", "{\"expectedRevision\":2}",
                "idem-stale", 1, "call-stale", "{}", "[]", "[]",
                "APPROVED", "{\"toolCode\":\"other_tool\"}", 3L);

        ToolCallResult result = toolCallApplicationService.executeToolCall(request);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TOOL_APPROVAL_STALE");
        verify(toolDefinitionSource, never()).getRequired(any());
        verify(toolCallExecutionService, never()).execute(any(), any());
    }

    private AuthorizedAgentRunContext context(ToolCallRequest request) {
        String traceId = switch (request.toolCallId()) {
            case "call-1" -> "trace-approval";
            case "call-recovery" -> "trace-recovery";
            case "call-missing" -> "trace-missing-handler";
            case "call-validate" -> "trace-validate";
            case "call-2" -> "trace-direct";
            case "call-stale" -> "trace-stale";
            default -> "trace-test";
        };
        return com.penmate.backend.application.agent.tool.runtime.AgentToolTestContext.context(
                1L, 11L, 9L, 501L, 7L, 601L, request.executionToken(), 701L, traceId);
    }
}
