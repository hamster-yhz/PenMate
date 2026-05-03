package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.catalog.StaticAgentToolCatalog;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResumeService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallSnapshotMapper;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentToolLoopRunnerTest {

    @Mock
    private AgentLlmGateway agentLlmGateway;

    @Mock
    private ToolCallApplicationService toolCallApplicationService;

    @Mock
    private StaticAgentToolCatalog staticAgentToolCatalog;

    @Mock
    private ToolCallResumeService toolCallResumeService;

    @Spy
    private ToolCallSnapshotMapper toolCallSnapshotMapper;

    @InjectMocks
    private AgentToolLoopRunner agentToolLoopRunner;

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_COMPLETE_MINIMAL_TWO_TURN_TOOL_CALL_LOOP() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<Map<String, Object>> initialMessages = List.of(Map.of(
                "role", "user",
                "content", "请补充这个场景需要的上下文"
        ));
        AgentLlmToolSchema contextEnhancerSchema = new AgentLlmToolSchema(
                "context_enhancer",
                "补充上下文",
                """
                        {
                          "type": "object",
                          "properties": {
                            "prompt": {
                              "type": "string"
                            }
                          },
                          "required": ["prompt"]
                        }
                        """
        );

        when(staticAgentToolCatalog.toLlmToolSchemas())
                .thenReturn(List.of(contextEnhancerSchema));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(new AgentLlmToolCall("call_1", "context_enhancer", "{\"prompt\":\"请补充这个场景需要的上下文\"}")),
                        "{\"finish_reason\":\"tool_calls\"}"
                ))
                .thenReturn(new AgentLlmTurnResponse(
                        "stop",
                        "这是补充上下文后的最终答案",
                        List.of(),
                        "{\"finish_reason\":\"stop\"}"
                ));
        when(toolCallApplicationService.executeToolCall(any()))
                .thenReturn(ToolCallResult.success("{\"context\":\"补充背景设定\"}"));

        AgentToolLoopIterationResult result = agentToolLoopRunner.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-1",
                initialMessages,
                executionConfig
        );

        assertThat(result.finalAssistantText()).isEqualTo("这是补充上下文后的最终答案");
        assertThat(result.waitingApproval()).isFalse();
        assertThat(result.toolCallCount()).isEqualTo(1);
        assertThat(result.toolContext()).isEqualTo("{\"context\":\"补充背景设定\"}");

        ArgumentCaptor<AgentLlmTurnRequest> requestCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(agentLlmGateway, times(2)).generateTurn(requestCaptor.capture(), eq(executionConfig));
        verify(toolCallApplicationService).executeToolCall(any());

        List<AgentLlmTurnRequest> requests = requestCaptor.getAllValues();
        AgentLlmTurnRequest firstRequest = requests.get(0);
        AgentLlmTurnRequest secondRequest = requests.get(1);

        assertThat(firstRequest.messages()).containsExactlyElementsOf(initialMessages);
        assertThat(firstRequest.tools()).containsExactly(contextEnhancerSchema);
        assertThat(firstRequest.toolChoice()).isEqualTo("auto");

        assertThat(secondRequest.messages()).hasSize(3);
        assertThat(secondRequest.messages().get(0))
                .containsEntry("role", "user")
                .containsEntry("content", "请补充这个场景需要的上下文");
        assertThat(secondRequest.messages().get(1))
                .containsEntry("role", "assistant")
                .containsEntry("content", "")
                .containsEntry("tool_calls", List.of(Map.of(
                        "id", "call_1",
                        "type", "function",
                        "function", Map.of(
                                "name", "context_enhancer",
                                "arguments", "{\"prompt\":\"请补充这个场景需要的上下文\"}"
                        )
                )));
        assertThat(secondRequest.messages().get(2))
                .containsEntry("role", "tool")
                .containsEntry("tool_call_id", "call_1")
                .containsEntry("content", "{\"context\":\"补充背景设定\"}");
        assertThat(secondRequest.tools()).containsExactly(contextEnhancerSchema);
        assertThat(secondRequest.toolChoice()).isEqualTo("auto");
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_FAIL_FAST_WHEN_TOOL_EXECUTION_FAILED() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<Map<String, Object>> initialMessages = List.of(Map.of(
                "role", "user",
                "content", "请先查上下文再回答"
        ));
        AgentLlmToolSchema contextEnhancerSchema = new AgentLlmToolSchema(
                "context_enhancer",
                "补充上下文",
                """
                        {
                          "type": "object",
                          "properties": {
                            "prompt": {
                              "type": "string"
                            }
                          },
                          "required": ["prompt"]
                        }
                        """
        );

        when(staticAgentToolCatalog.toLlmToolSchemas())
                .thenReturn(List.of(contextEnhancerSchema));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(new AgentLlmToolCall("call_1", "context_enhancer", "{\"prompt\":\"请先查上下文再回答\"}")),
                        "{\"finish_reason\":\"tool_calls\"}"
                ));
        when(toolCallApplicationService.executeToolCall(any()))
                .thenReturn(new ToolCallResult(
                        "FAILED",
                        null,
                        null,
                        "TOOL_EXECUTION_FAILED",
                        "context enhancer timeout"
                ));

        assertThatThrownBy(() -> agentToolLoopRunner.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-2",
                initialMessages,
                executionConfig
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOOL_EXECUTION_FAILED")
                .hasMessageContaining("context enhancer timeout");

        verify(agentLlmGateway, times(1)).generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig));
        verify(toolCallApplicationService).executeToolCall(any());
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_RETURN_WAITING_APPROVAL_ON_PENDING_TOOL_APPROVAL() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<Map<String, Object>> initialMessages = List.of(Map.of(
                "role", "user",
                "content", "删除书籍"
        ));

        when(staticAgentToolCatalog.toLlmToolSchemas())
                .thenReturn(List.of(new AgentLlmToolSchema("context_enhancer", "补充上下文", "{\"type\":\"object\"}")));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(new AgentLlmToolCall("call_9", "book_crud", "{\"operation\":\"delete\",\"projectId\":9001}")),
                        "{\"finish_reason\":\"tool_calls\"}"
                ));
        when(toolCallApplicationService.executeToolCall(any()))
                .thenReturn(ToolCallResult.waitingApproval(99L));

        AgentToolLoopIterationResult result = agentToolLoopRunner.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-4",
                initialMessages,
                executionConfig
        );

        assertThat(result.waitingApproval()).isTrue();
        assertThat(result.approvalId()).isEqualTo(99L);
        assertThat(result.toolCallCount()).isEqualTo(1);
        verify(agentLlmGateway, times(1)).generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig));
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_CARRY_TOOL_CONTEXT_ACROSS_MULTIPLE_TOOL_CALLS_BEFORE_APPROVAL() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<Map<String, Object>> initialMessages = List.of(Map.of(
                "role", "user",
                "content", "先补充上下文再删除"
        ));

        when(staticAgentToolCatalog.toLlmToolSchemas())
                .thenReturn(List.of(new AgentLlmToolSchema("context_enhancer", "补充上下文", "{\"type\":\"object\"}")));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(
                                new AgentLlmToolCall("call_1", "context_enhancer", "{\"prompt\":\"先补充上下文再删除\"}"),
                                new AgentLlmToolCall("call_2", "book_crud", "{\"operation\":\"delete\",\"projectId\":9001}")
                        ),
                        "{\"finish_reason\":\"tool_calls\"}"
                ));
        when(toolCallApplicationService.executeToolCall(any()))
                .thenReturn(ToolCallResult.success("{\"context\":\"补充背景设定\"}"))
                .thenReturn(ToolCallResult.waitingApproval(88L));

        AgentToolLoopIterationResult result = agentToolLoopRunner.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-6",
                initialMessages,
                executionConfig
        );

        assertThat(result.waitingApproval()).isTrue();
        ArgumentCaptor<ToolCallRequest> requestCaptor = ArgumentCaptor.forClass(ToolCallRequest.class);
        verify(toolCallApplicationService, times(2)).executeToolCall(requestCaptor.capture());
        ToolCallRequest secondRequest = requestCaptor.getAllValues().get(1);

        assertThat(secondRequest.assistantToolCallsJson()).contains("call_1").contains("call_2");
        assertThat(result.toolContext()).isEqualTo("{\"context\":\"补充背景设定\"}");
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_DELEGATE_APPROVAL_RESUME_TO_RESUME_SERVICE() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(88L);
        PendingToolInvocationSnapshot snapshot = new PendingToolInvocationSnapshot(
                88L,
                1L,
                11L,
                9L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9}",
                "{}",
                0L,
                "trace-review-2",
                "call_2-11",
                "executing",
                "trace-review-2-loop",
                0,
                "call_2",
                "[]",
                "[]",
                "RESUME_LOOP",
                "{\"approvalType\":\"BOOK_DELETE\"}"
        );
        ToolCallResult expected = ToolCallResult.success("最终答复");
        when(toolCallResumeService.resumeFromPending(request, snapshot)).thenReturn(expected);

        ToolCallResult result = agentToolLoopRunner.resumeFromPending(request, snapshot);

        assertThat(result).isSameAs(expected);
        verify(toolCallResumeService).resumeFromPending(request, snapshot);
        verify(agentLlmGateway, never()).generateTurn(any(), any());
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_FAIL_WHEN_SINGLE_TURN_EXCEEDS_MAX_TOOL_CALLS_PER_TURN() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<Map<String, Object>> initialMessages = List.of(Map.of(
                "role", "user",
                "content", "请依次执行四个工具"
        ));

        when(staticAgentToolCatalog.toLlmToolSchemas()).thenReturn(List.of(
                new AgentLlmToolSchema("context_enhancer", "补充上下文", "{\"type\":\"object\"}")
        ));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        IntStream.rangeClosed(1, 4)
                                .mapToObj(index -> new AgentLlmToolCall("call_" + index, "context_enhancer", "{\"prompt\":\"p\"}"))
                                .toList(),
                        "{\"finish_reason\":\"tool_calls\"}"
                ));

        assertThatThrownBy(() -> agentToolLoopRunner.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-7",
                initialMessages,
                executionConfig
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max tool calls per turn");
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_FAIL_WHEN_TOTAL_TURNS_EXCEED_LIMIT() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<Map<String, Object>> initialMessages = List.of(Map.of(
                "role", "user",
                "content", "不停调用工具"
        ));

        when(staticAgentToolCatalog.toLlmToolSchemas()).thenReturn(List.of(
                new AgentLlmToolSchema("context_enhancer", "补充上下文", "{\"type\":\"object\"}")
        ));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(new AgentLlmToolCall("call_loop", "context_enhancer", "{\"prompt\":\"loop\"}")),
                        "{\"finish_reason\":\"tool_calls\"}"
                ));
        when(toolCallApplicationService.executeToolCall(any()))
                .thenReturn(ToolCallResult.success("{\"context\":\"ok\"}"));

        assertThatThrownBy(() -> agentToolLoopRunner.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-8",
                initialMessages,
                executionConfig
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max turns");
    }
}
