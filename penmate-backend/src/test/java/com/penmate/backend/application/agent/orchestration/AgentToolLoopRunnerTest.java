package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.runtime.RuntimeStatusView;
import com.penmate.backend.application.agent.runtime.TaskRuntimeStatusPublisher;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.llm.LlmTokenUsage;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResumeService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallSnapshotMapper;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;

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
    private AgentToolDefinitionSource toolDefinitionSource;

    @Mock
    private ToolCallResumeService toolCallResumeService;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private TaskRuntimeStatusPublisher taskRuntimeStatusPublisher;

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
        List<AgentLlmMessage> initialMessages = List.of(AgentLlmMessage.user("请补充这个场景需要的上下文"));
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

        when(toolDefinitionSource.listLlmSchemas())
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

        assertThat(firstRequest.messages()).containsExactly(
                com.penmate.backend.domain.agent.model.AgentLlmMessage.user("请补充这个场景需要的上下文")
        );
        assertThat(firstRequest.tools()).containsExactly(contextEnhancerSchema);
        assertThat(firstRequest.toolChoice()).isEqualTo("auto");

        assertThat(secondRequest.messages()).hasSize(3);
        assertThat(secondRequest.messages().get(0).role())
                .isEqualTo(com.penmate.backend.domain.agent.model.AgentLlmMessageRole.USER);
        assertThat(secondRequest.messages().get(0).content())
                .isEqualTo("请补充这个场景需要的上下文");
        assertThat(secondRequest.messages().get(1).role())
                .isEqualTo(com.penmate.backend.domain.agent.model.AgentLlmMessageRole.ASSISTANT);
        assertThat(secondRequest.messages().get(1).content()).isEqualTo("");
        assertThat(secondRequest.messages().get(1).toolCalls()).containsExactly(
                new com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload(
                        "call_1",
                        "function",
                        "context_enhancer",
                        "{\"prompt\":\"请补充这个场景需要的上下文\"}"
                )
        );
        assertThat(secondRequest.messages().get(2).role())
                .isEqualTo(com.penmate.backend.domain.agent.model.AgentLlmMessageRole.TOOL);
        assertThat(secondRequest.messages().get(2).toolCallId()).isEqualTo("call_1");
        assertThat(secondRequest.messages().get(2).content()).isEqualTo("{\"context\":\"补充背景设定\"}");
        assertThat(secondRequest.tools()).containsExactly(contextEnhancerSchema);
        assertThat(secondRequest.toolChoice()).isEqualTo("auto");
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_ACCUMULATE_TOKEN_USAGE_ACROSS_MULTIPLE_LLM_TURNS() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<AgentLlmMessage> initialMessages = List.of(AgentLlmMessage.user("请补充这个场景需要的上下文"));
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

        when(toolDefinitionSource.listLlmSchemas())
                .thenReturn(List.of(contextEnhancerSchema));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(new AgentLlmToolCall("call_1", "context_enhancer", "{\"prompt\":\"请补充这个场景需要的上下文\"}")),
                        "{\"finish_reason\":\"tool_calls\"}",
                        new LlmTokenUsage(11, 7, 18)
                ))
                .thenReturn(new AgentLlmTurnResponse(
                        "stop",
                        "这是补充上下文后的最终答案",
                        List.of(),
                        "{\"finish_reason\":\"stop\"}",
                        new LlmTokenUsage(5, 13, 18)
                ));
        when(toolCallApplicationService.executeToolCall(any()))
                .thenReturn(ToolCallResult.success("{\"context\":\"补充背景设定\"}"));

        AgentToolLoopIterationResult result = agentToolLoopRunner.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-token-usage",
                initialMessages,
                executionConfig
        );

        assertThat(result.tokenUsage()).isEqualTo(new LlmTokenUsage(16, 20, 36));
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_FAIL_FAST_WHEN_TOOL_EXECUTION_FAILED() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<AgentLlmMessage> initialMessages = List.of(AgentLlmMessage.user("请先查上下文再回答"));
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

        when(toolDefinitionSource.listLlmSchemas())
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
        List<AgentLlmMessage> initialMessages = List.of(AgentLlmMessage.user("删除书籍"));

        when(toolDefinitionSource.listLlmSchemas())
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
        assertThat(result.tokenUsage()).isEqualTo(LlmTokenUsage.ZERO);
        verify(agentLlmGateway, times(1)).generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig));
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_RETURN_ACCUMULATED_TOKEN_USAGE_WHEN_WAITING_APPROVAL() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<AgentLlmMessage> initialMessages = List.of(AgentLlmMessage.user("删除书籍"));

        when(toolDefinitionSource.listLlmSchemas())
                .thenReturn(List.of(new AgentLlmToolSchema("context_enhancer", "补充上下文", "{\"type\":\"object\"}")));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(new AgentLlmToolCall("call_9", "book_crud", "{\"operation\":\"delete\",\"projectId\":9001}")),
                        "{\"finish_reason\":\"tool_calls\"}",
                        new LlmTokenUsage(12, 4, 16)
                ));
        when(toolCallApplicationService.executeToolCall(any()))
                .thenReturn(ToolCallResult.waitingApproval(99L));

        AgentToolLoopIterationResult result = agentToolLoopRunner.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-4-usage",
                initialMessages,
                executionConfig
        );

        assertThat(result.waitingApproval()).isTrue();
        assertThat(result.approvalId()).isEqualTo(99L);
        assertThat(result.toolCallCount()).isEqualTo(1);
        assertThat(result.tokenUsage()).isEqualTo(new LlmTokenUsage(12, 4, 16));
        verify(agentLlmGateway, times(1)).generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig));
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_PUBLISH_STRUCTURED_TOOL_RUNTIME_STATUS_ON_WAITING_APPROVAL() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<AgentLlmMessage> initialMessages = List.of(AgentLlmMessage.user("删除书籍"));

        AgentTaskContext persistedContext = AgentTaskContext.runningOf(901L, 11L, "RUNNING", 3001L, "片段");
        persistedContext.setTurnId(50011L);

        when(agentRepository.findTaskContext(11L)).thenReturn(persistedContext);
        when(agentRepository.updateGenerationTaskSnapshots(eq(1L), eq(11L), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(toolDefinitionSource.listLlmSchemas())
                .thenReturn(List.of(new AgentLlmToolSchema("book_crud", "书籍 CRUD", "{\"type\":\"object\"}")));
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
                "trace-runtime-tool",
                initialMessages,
                executionConfig
        );

        assertThat(result.waitingApproval()).isTrue();
        ArgumentCaptor<RuntimeStatusView> toolCallCaptor = ArgumentCaptor.forClass(RuntimeStatusView.class);
        verify(taskRuntimeStatusPublisher).publishToolCall(eq(1L), toolCallCaptor.capture());

        assertThat(toolCallCaptor.getValue().taskId()).isEqualTo(11L);
        assertThat(toolCallCaptor.getValue().sessionId()).isEqualTo(9L);
        assertThat(toolCallCaptor.getValue().phase()).isEqualTo("tool_call");
        assertThat(toolCallCaptor.getValue().toolCall()).isNotNull();
        assertThat(toolCallCaptor.getValue().toolCall().toolCode()).isEqualTo("book_crud");
        assertThat(toolCallCaptor.getValue().toolCall().status()).isEqualTo("waiting_approval");
        assertThat(toolCallCaptor.getValue().recoverable()).isTrue();
        assertThat(persistedContext.getLastRuntimeStatus()).isEqualTo("tool_call");
        assertThat(persistedContext.getRecoveryCursor()).isEqualTo("approval:99");
        assertThat(persistedContext.getActiveToolCallsSnapshot()).contains("\"toolCallId\":\"call_9\"");
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_CARRY_TOOL_CONTEXT_ACROSS_MULTIPLE_TOOL_CALLS_BEFORE_APPROVAL() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<AgentLlmMessage> initialMessages = List.of(AgentLlmMessage.user("先补充上下文再删除"));

        when(toolDefinitionSource.listLlmSchemas())
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
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_FEED_STRUCTURED_DRAFT_TOOL_OUTPUT_BACK_TO_NEXT_TURN_MESSAGES() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<AgentLlmMessage> initialMessages = List.of(AgentLlmMessage.user("请先生成第三章正文初稿"));
        AgentLlmToolSchema draftGenerationSchema = new AgentLlmToolSchema(
                "draft_generation",
                "生成正文、改写正文或套用修订",
                "{\"type\":\"object\"}"
        );
        String draftResultJson = "{\"draftText\":\"第三章初稿正文\",\"operation\":\"generate\",\"preservedConstraints\":[\"保留第一人称\",\"保留女主冷静口吻\"],\"sourceSummary\":\"第三章冲突提纲\"}";

        when(toolDefinitionSource.listLlmSchemas())
                .thenReturn(List.of(draftGenerationSchema));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(new AgentLlmToolCall(
                                "call_draft_1",
                                "draft_generation",
                                "{\"operation\":\"generate\",\"prompt\":\"请先生成第三章正文初稿\",\"preservedConstraints\":[\"保留第一人称\",\"保留女主冷静口吻\"],\"sourceSummary\":\"第三章冲突提纲\"}"
                        )),
                        "{\"finish_reason\":\"tool_calls\"}"
                ))
                .thenReturn(new AgentLlmTurnResponse(
                        "stop",
                        "这是基于初稿整理后的最终答复",
                        List.of(),
                        "{\"finish_reason\":\"stop\"}"
                ));
        when(toolCallApplicationService.executeToolCall(any()))
                .thenReturn(ToolCallResult.success(draftResultJson));

        AgentToolLoopIterationResult result = agentToolLoopRunner.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-draft-1",
                initialMessages,
                executionConfig
        );

        assertThat(result.waitingApproval()).isFalse();
        assertThat(result.finalAssistantText()).isEqualTo("这是基于初稿整理后的最终答复");
        assertThat(result.toolCallCount()).isEqualTo(1);
        assertThat(result.toolContext()).isEqualTo(draftResultJson);

        ArgumentCaptor<AgentLlmTurnRequest> requestCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(agentLlmGateway, times(2)).generateTurn(requestCaptor.capture(), eq(executionConfig));
        AgentLlmTurnRequest secondRequest = requestCaptor.getAllValues().get(1);

        assertThat(secondRequest.messages()).hasSize(3);
        assertThat(secondRequest.messages().get(2).role())
                .isEqualTo(com.penmate.backend.domain.agent.model.AgentLlmMessageRole.TOOL);
        assertThat(secondRequest.messages().get(2).toolCallId()).isEqualTo("call_draft_1");
        assertThat(secondRequest.messages().get(2).content()).isEqualTo(draftResultJson);
        assertThat(secondRequest.messages().get(2).content())
                .contains("\"draftText\":\"第三章初稿正文\"")
                .contains("\"operation\":\"generate\"")
                .contains("\"preservedConstraints\":[\"保留第一人称\",\"保留女主冷静口吻\"]")
                .contains("\"sourceSummary\":\"第三章冲突提纲\"");
    }

    @Test
    void UT_APP_AGENT_TOOL_LOOP_RUNNER_SHOULD_FEED_STRUCTURED_TODO_PLAN_TOOL_OUTPUT_BACK_TO_NEXT_TURN_MESSAGES() {
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
        List<AgentLlmMessage> initialMessages = List.of(AgentLlmMessage.user("请先整理第三章修订待办"));
        AgentLlmToolSchema todoPlannerSchema = new AgentLlmToolSchema(
                "todo_planner",
                "将用户请求、质量问题与后续规划整理为结构化 Todo 规划建议",
                "{\"type\":\"object\"}"
        );
        String todoPlanJson = "{\"planTitle\":\"第三章修订待办\",\"planSummary\":\"整理修订动作\",\"recommendedNextAction\":\"先修复 P0 问题\",\"items\":[{\"title\":\"修复主角提前知情\",\"description\":\"删除越界知情描写并补充情报来源\",\"priority\":\"P0\",\"sourceType\":\"QUALITY_REVIEW\",\"recommendedStatus\":\"TODO\",\"suggestedAutoCreate\":true,\"rationale\":\"高风险剧情逻辑问题\",\"acceptanceCriteria\":[\"情报来源合理\"],\"dependsOn\":[]}]}";

        when(toolDefinitionSource.listLlmSchemas())
                .thenReturn(List.of(todoPlannerSchema));
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse(
                        "tool_calls",
                        "",
                        List.of(new AgentLlmToolCall(
                                "call_todo_1",
                                "todo_planner",
                                "{\"planningMode\":\"FOLLOW_UP_MODIFICATION\",\"userRequest\":\"请先整理第三章修订待办\"}"
                        )),
                        "{\"finish_reason\":\"tool_calls\"}"
                ))
                .thenReturn(new AgentLlmTurnResponse(
                        "stop",
                        "这是基于 todo 规划整理后的最终答复",
                        List.of(),
                        "{\"finish_reason\":\"stop\"}"
                ));
        when(toolCallApplicationService.executeToolCall(any()))
                .thenReturn(ToolCallResult.success(todoPlanJson));

        AgentToolLoopIterationResult result = agentToolLoopRunner.execute(
                1L,
                11L,
                9L,
                0L,
                "trace-todo-1",
                initialMessages,
                executionConfig
        );

        assertThat(result.waitingApproval()).isFalse();
        assertThat(result.finalAssistantText()).isEqualTo("这是基于 todo 规划整理后的最终答复");
        assertThat(result.toolCallCount()).isEqualTo(1);
        assertThat(result.toolContext()).isEqualTo(todoPlanJson);

        ArgumentCaptor<AgentLlmTurnRequest> requestCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(agentLlmGateway, times(2)).generateTurn(requestCaptor.capture(), eq(executionConfig));
        AgentLlmTurnRequest secondRequest = requestCaptor.getAllValues().get(1);

        assertThat(secondRequest.messages()).hasSize(3);
        assertThat(secondRequest.messages().get(2).role())
                .isEqualTo(com.penmate.backend.domain.agent.model.AgentLlmMessageRole.TOOL);
        assertThat(secondRequest.messages().get(2).toolCallId()).isEqualTo("call_todo_1");
        assertThat(secondRequest.messages().get(2).content()).isEqualTo(todoPlanJson);
        assertThat(secondRequest.messages().get(2).content())
                .contains("\"planTitle\":\"第三章修订待办\"")
                .contains("\"recommendedNextAction\":\"先修复 P0 问题\"")
                .contains("\"sourceType\":\"QUALITY_REVIEW\"")
                .contains("\"recommendedStatus\":\"TODO\"");
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
        List<AgentLlmMessage> initialMessages = List.of(AgentLlmMessage.user("请依次执行四个工具"));

        when(toolDefinitionSource.listLlmSchemas()).thenReturn(List.of(
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
        List<AgentLlmMessage> initialMessages = List.of(AgentLlmMessage.user("不停调用工具"));

        when(toolDefinitionSource.listLlmSchemas()).thenReturn(List.of(
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
