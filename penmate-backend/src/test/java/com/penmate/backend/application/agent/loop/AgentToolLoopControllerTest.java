package com.penmate.backend.application.agent.loop;

import com.penmate.backend.application.agent.ToolInvocationRequest;
import com.penmate.backend.application.agent.StaticToolMetadataRegistry;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.ToolInvocationGateway;
import com.penmate.backend.application.agent.ToolInvocationGatewayResult;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentToolLoopControllerTest {

    @Mock
    private AgentLlmGateway agentLlmGateway;

    @Mock
    private ToolInvocationGateway toolInvocationGateway;

    @Mock
    private StaticToolMetadataRegistry staticToolMetadataRegistry;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentModelRoutingService agentModelRoutingService;

    @InjectMocks
    private AgentToolLoopController agentToolLoopController;

    @Test
    void UT_APP_AGENT_TOOL_LOOP_CONTROLLER_SHOULD_COMPLETE_MINIMAL_TWO_TURN_TOOL_CALL_LOOP() {
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

        when(staticToolMetadataRegistry.toLlmToolSchemas())
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
        when(toolInvocationGateway.invoke(any()))
                .thenReturn(ToolInvocationGatewayResult.success("{\"context\":\"补充背景设定\"}"));

        AgentToolLoopIterationResult result = agentToolLoopController.execute(
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
        verify(toolInvocationGateway).invoke(any());

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
    void UT_APP_AGENT_TOOL_LOOP_CONTROLLER_SHOULD_FAIL_FAST_WHEN_TOOL_EXECUTION_FAILED() {
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

        when(staticToolMetadataRegistry.toLlmToolSchemas())
                .thenReturn(List.of(contextEnhancerSchema));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(new AgentLlmToolCall("call_1", "context_enhancer", "{\"prompt\":\"请先查上下文再回答\"}")),
                        "{\"finish_reason\":\"tool_calls\"}"
                ));
        when(toolInvocationGateway.invoke(any()))
                .thenReturn(new ToolInvocationGatewayResult(
                        "FAILED",
                        null,
                        null,
                        "TOOL_EXECUTION_FAILED",
                        "context enhancer timeout"
                ));

        assertThatThrownBy(() -> agentToolLoopController.execute(
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
        verify(toolInvocationGateway).invoke(any());
        verify(agentLlmGateway, never()).generateTurn(argThat(request -> request.messages().size() > 1), eq(executionConfig));
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_CONTROLLER_SHOULD_FAIL_FAST_WHEN_PROVIDER_RETURNS_TOOL_CALLS_WITHOUT_CALL_LIST() {
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

        when(staticToolMetadataRegistry.toLlmToolSchemas())
                .thenReturn(List.of(contextEnhancerSchema));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(),
                        "{\"finish_reason\":\"tool_calls\"}"
                ));

        assertThatThrownBy(() -> agentToolLoopController.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-3",
                initialMessages,
                executionConfig
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tool_calls")
                .hasMessageContaining("empty");

        verify(agentLlmGateway, times(1)).generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig));
        verify(toolInvocationGateway, never()).invoke(any());
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_CONTROLLER_SHOULD_STOP_LOOP_AND_RETURN_APPROVAL_ID_WHEN_TOOL_WAITS_APPROVAL() {
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

        when(staticToolMetadataRegistry.toLlmToolSchemas())
                .thenReturn(List.of(contextEnhancerSchema));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(new AgentLlmToolCall("call_1", "context_enhancer", "{\"prompt\":\"请先查上下文再回答\"}")),
                        "{\"finish_reason\":\"tool_calls\"}"
                ));
        when(toolInvocationGateway.invoke(any()))
                .thenReturn(ToolInvocationGatewayResult.waitingApproval(99L));

        AgentToolLoopIterationResult result = agentToolLoopController.execute(
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
        assertThat(result.finalAssistantText()).isEmpty();
        verify(agentLlmGateway, times(1)).generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig));
        verify(toolInvocationGateway).invoke(any());
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_CONTROLLER_SHOULD_FAIL_FAST_WHEN_WAITING_APPROVAL_HAS_NULL_APPROVAL_ID() {
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

        when(staticToolMetadataRegistry.toLlmToolSchemas())
                .thenReturn(List.of(contextEnhancerSchema));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(new AgentLlmToolCall("call_1", "context_enhancer", "{\"prompt\":\"请先查上下文再回答\"}")),
                        "{\"finish_reason\":\"tool_calls\"}"
                ));
        when(toolInvocationGateway.invoke(any()))
                .thenReturn(ToolInvocationGatewayResult.waitingApproval(null));

        assertThatThrownBy(() -> agentToolLoopController.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-5",
                initialMessages,
                executionConfig
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approvalId");
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_CONTROLLER_SHOULD_INCLUDE_PREVIOUS_TOOL_RESULTS_IN_PENDING_REQUEST_CONTEXT() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<Map<String, Object>> initialMessages = List.of(Map.of(
                "role", "user",
                "content", "请先查上下文，再删除项目"
        ));
        AgentLlmToolSchema schema = new AgentLlmToolSchema(
                "context_enhancer",
                "补充上下文",
                "{\"type\":\"object\"}"
        );

        when(staticToolMetadataRegistry.toLlmToolSchemas()).thenReturn(List.of(schema));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(
                                new AgentLlmToolCall("call_1", "context_enhancer", "{\"prompt\":\"查背景\"}"),
                                new AgentLlmToolCall("call_2", "book_crud", "{\"operation\":\"delete\",\"projectId\":9}")
                        ),
                        "{\"finish_reason\":\"tool_calls\"}"
                ));
        when(toolInvocationGateway.invoke(any()))
                .thenReturn(ToolInvocationGatewayResult.success("{\"context\":\"补充背景设定\"}"))
                .thenReturn(ToolInvocationGatewayResult.waitingApproval(88L));

        AgentToolLoopIterationResult result = agentToolLoopController.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-review-1",
                initialMessages,
                executionConfig
        );

        assertThat(result.waitingApproval()).isTrue();
        ArgumentCaptor<ToolInvocationRequest> requestCaptor = ArgumentCaptor.forClass(ToolInvocationRequest.class);
        verify(toolInvocationGateway, times(2)).invoke(requestCaptor.capture());
        ToolInvocationRequest secondRequest = requestCaptor.getAllValues().get(1);

        assertThat(secondRequest.resumeMode()).isEqualTo("RESUME_LOOP");
        assertThat(secondRequest.conversationMessagesJson()).contains("tool_call_id");
        assertThat(secondRequest.conversationMessagesJson()).contains("call_1");
        assertThat(secondRequest.conversationMessagesJson()).contains("补充背景设定");
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_CONTROLLER_RESUME_FROM_PENDING_SHOULD_KEEP_PREVIOUS_AND_APPROVED_TOOL_RESULTS_IN_NEXT_LLM_REQUEST() {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(88L);
        request.setProjectId(1L);
        request.setTaskId(11L);
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
                "[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"context_enhancer\",\"arguments\":\"{\\\"prompt\\\":\\\"查背景\\\"}\"}},{\"id\":\"call_2\",\"type\":\"function\",\"function\":{\"name\":\"book_crud\",\"arguments\":\"{\\\"operation\\\":\\\"delete\\\",\\\"projectId\\\":9}\"}}]",
                "[{\"role\":\"user\",\"content\":\"请先查上下文，再删除项目\"},{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"context_enhancer\",\"arguments\":\"{\\\"prompt\\\":\\\"查背景\\\"}\"}},{\"id\":\"call_2\",\"type\":\"function\",\"function\":{\"name\":\"book_crud\",\"arguments\":\"{\\\"operation\\\":\\\"delete\\\",\\\"projectId\\\":9}\"}}]},{\"role\":\"tool\",\"tool_call_id\":\"call_1\",\"content\":\"{\\\"context\\\":\\\"补充背景设定\\\"}\"}]",
                "RESUME_LOOP",
                "{\"approvalType\":\"BOOK_DELETE\"}"
        );
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(11L);
        task.setModelConfigId(100L);
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        AgentLlmToolSchema schema = new AgentLlmToolSchema(
                "context_enhancer",
                "补充上下文",
                "{\"type\":\"object\"}"
        );

        when(toolInvocationGateway.resume(snapshot)).thenReturn(ToolInvocationGatewayResult.success("{\"result\":\"deleted\"}"));
        when(agentRepository.findGenerationTask(1L, 11L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1L, 100L, "trace-review-2")).thenReturn(executionConfig);
        when(staticToolMetadataRegistry.toLlmToolSchemas()).thenReturn(List.of(schema));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", "最终答复", List.of(), "{\"finish_reason\":\"stop\"}"));

        ToolInvocationGatewayResult result = agentToolLoopController.resumeFromPending(request, snapshot);

        assertThat(result.status()).isEqualTo("SUCCESS");
        ArgumentCaptor<AgentLlmTurnRequest> llmRequestCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(agentLlmGateway).generateTurn(llmRequestCaptor.capture(), eq(executionConfig));
        List<Map<String, Object>> messages = llmRequestCaptor.getValue().messages();

        assertThat(IntStream.range(0, messages.size())
                .filter(index -> "tool".equals(messages.get(index).get("role")))
                .mapToObj(messages::get)
                .toList())
                .anySatisfy(message -> assertThat(message)
                        .containsEntry("tool_call_id", "call_1")
                        .containsEntry("content", "{\"context\":\"补充背景设定\"}"))
                .anySatisfy(message -> assertThat(message)
                        .containsEntry("tool_call_id", "call_2")
                        .containsEntry("content", "{\"result\":\"deleted\"}"));
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_CONTROLLER_SHOULD_FAIL_WHEN_SINGLE_TURN_EXCEEDS_MAX_TOOL_CALLS_PER_TURN() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<Map<String, Object>> initialMessages = List.of(Map.of(
                "role", "user",
                "content", "请依次执行四个工具"
        ));

        when(staticToolMetadataRegistry.toLlmToolSchemas()).thenReturn(List.of(
                new AgentLlmToolSchema("context_enhancer", "补充上下文", "{\"type\":\"object\"}")
        ));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(
                                new AgentLlmToolCall("call_1", "context_enhancer", "{\"prompt\":\"1\"}"),
                                new AgentLlmToolCall("call_2", "context_enhancer", "{\"prompt\":\"2\"}"),
                                new AgentLlmToolCall("call_3", "context_enhancer", "{\"prompt\":\"3\"}"),
                                new AgentLlmToolCall("call_4", "context_enhancer", "{\"prompt\":\"4\"}")
                        ),
                        "{\"finish_reason\":\"tool_calls\"}"
                ));

        assertThatThrownBy(() -> agentToolLoopController.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-max-per-turn",
                initialMessages,
                executionConfig
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max tool calls per turn")
                .hasMessageContaining("3");

        verify(toolInvocationGateway, never()).invoke(any());
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_CONTROLLER_SHOULD_FAIL_WHEN_LOOP_TURNS_EXCEED_CONSERVATIVE_LIMIT() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<Map<String, Object>> initialMessages = List.of(Map.of(
                "role", "user",
                "content", "一直请求工具"
        ));

        when(staticToolMetadataRegistry.toLlmToolSchemas()).thenReturn(List.of(
                new AgentLlmToolSchema("context_enhancer", "补充上下文", "{\"type\":\"object\"}")
        ));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(new AgentLlmToolCall("call_loop", "context_enhancer", "{\"prompt\":\"loop\"}")),
                        "{\"finish_reason\":\"tool_calls\"}"
                ));
        when(toolInvocationGateway.invoke(any()))
                .thenReturn(ToolInvocationGatewayResult.success("{\"context\":\"ok\"}"));

        assertThatThrownBy(() -> agentToolLoopController.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-max-turns",
                initialMessages,
                executionConfig
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max turns")
                .hasMessageContaining("4");

        verify(agentLlmGateway, times(4)).generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig));
        verify(toolInvocationGateway, times(4)).invoke(any());
    }
}
