package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolCallResumeServiceTest {

    @Mock
    private ToolCallApplicationService toolCallApplicationService;

    @Mock
    private AgentLlmGateway agentLlmGateway;

    @Mock
    private AgentToolDefinitionSource toolDefinitionSource;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentModelRoutingService agentModelRoutingService;

    @Mock
    private ToolCallSnapshotMapper toolCallSnapshotMapper;

    private ToolCallResumeService toolCallResumeService;

    @BeforeEach
    void setUp() {
        toolCallResumeService = new ToolCallResumeService(
                toolCallApplicationService,
                agentLlmGateway,
                toolDefinitionSource,
                agentRepository,
                agentModelRoutingService,
                toolCallSnapshotMapper
        );
    }

    @Test
    void UT_APP_AGENT_TOOL_CALL_RESUME_SERVICE_SHOULD_FAIL_FAST_WHEN_APPROVED_TOOL_REQUIRES_APPROVAL_AGAIN() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(88L);
        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                88L,
                1L,
                11L,
                9L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9001}",
                "{}",
                7L,
                "trace-resume-1",
                "call-1-11",
                "pending",
                "trace-resume-1-loop",
                0,
                "call-1",
                "[{\"id\":\"call-1\",\"function\":{\"name\":\"book_crud\",\"arguments\":\"{\\\"operation\\\":\\\"delete\\\",\\\"projectId\\\":9001}\"}}]",
                "[{\"role\":\"user\",\"content\":\"delete\"}]",
                "RESUME_LOOP",
                "{\"approvalType\":\"BOOK_DELETE\"}"
        );

        when(toolCallSnapshotMapper.parseMessages(snapshot.conversationMessagesJson()))
                .thenReturn(List.of(Map.of("role", "user", "content", "delete")));
        when(toolCallApplicationService.executeToolCall(any()))
                .thenReturn(ToolCallResult.waitingApproval(99L));

        assertThatThrownBy(() -> toolCallResumeService.resumeFromPending(request, snapshot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved tool invocation cannot require approval again")
                .hasMessageContaining("book_crud");

        verify(agentLlmGateway, never()).generateTurn(any(), any());
    }

    @Test
    void UT_APP_AGENT_TOOL_CALL_RESUME_SERVICE_SHOULD_USE_TOOL_DEFINITION_SOURCE_LLM_SCHEMAS_WHEN_RESUMING_LOOP() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(88L);
        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                88L,
                1L,
                11L,
                9L,
                "book_crud",
                "{\"operation\":\"list\"}",
                "{}",
                7L,
                "trace-resume-2",
                "call-1-11",
                "pending",
                "trace-resume-2-loop",
                0,
                "call-1",
                "[{\"id\":\"call-1\",\"function\":{\"name\":\"book_crud\",\"arguments\":\"{\\\"operation\\\":\\\"list\\\"}\"}}]",
                "[{\"role\":\"user\",\"content\":\"list books\"}]",
                "RESUME_LOOP",
                "{\"approvalType\":\"BOOK_LIST\"}"
        );
        AgentGenerationTask task = new AgentGenerationTask();
        task.setModelConfigId(66L);
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .modelConfigId(66L)
                .providerCode("provider")
                .baseUrl("https://example.test")
                .apiKey("key")
                .modelName("model")
                .keySource("USER_KEY")
                .build();

        when(toolCallSnapshotMapper.parseMessages(snapshot.conversationMessagesJson()))
                .thenReturn(new ArrayList<>(List.of(Map.of("role", "user", "content", "list books"))));
        when(toolCallSnapshotMapper.parseToolCallPayloads(snapshot.assistantToolCallsJson()))
                .thenReturn(List.of(Map.of(
                        "id", "call-1",
                        "function", Map.of("name", "book_crud", "arguments", "{\"operation\":\"list\"}")
                )));
        when(toolCallApplicationService.executeToolCall(any()))
                .thenReturn(ToolCallResult.success("{\"items\":[]}"));
        when(agentRepository.findGenerationTask(1L, 11L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1L, 66L, "trace-resume-2")).thenReturn(executionConfig);
        when(toolDefinitionSource.listLlmSchemas()).thenReturn(List.of(
                new AgentLlmToolSchema("book_crud", "书籍 CRUD；必须提供 operation", "{\"type\":\"object\"}")
        ));
        when(agentLlmGateway.generateTurn(any(), any()))
                .thenReturn(new AgentLlmTurnResponse("stop", "done", List.of(), "{}"));

        ToolCallResult result = toolCallResumeService.resumeFromPending(request, snapshot);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput()).isEqualTo("done");
        ArgumentCaptor<AgentLlmTurnRequest> requestCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(agentLlmGateway).generateTurn(requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().tools()).hasSize(1);
        assertThat(requestCaptor.getValue().tools().get(0).toolCode()).isEqualTo("book_crud");
    }
}
