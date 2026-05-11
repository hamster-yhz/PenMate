package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.context.AgentContextRoutingFacade;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.context.AgentContextRoutingRequest;
import com.penmate.backend.application.agent.context.AgentContextRoutingResult;
import com.penmate.backend.application.agent.context.StoryBibleContextResult;
import com.penmate.backend.application.agent.orchestration.preflight.AgentBehaviorType;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightCoordinator;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import com.penmate.backend.application.rag.RagRetrievalService;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentGenerationWorkflowTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private com.penmate.backend.application.agent.AgentTaskStateMachine taskStateMachine;

    @Mock
    private RealtimeEventService realtimeEventService;

    @Mock
    private AgentToolLoopRunner agentToolLoopRunner;

    @Mock
    private RagRetrievalService ragRetrievalService;

    @Mock
    private AgentModelRoutingService agentModelRoutingService;

    @Mock
    private AgentPromptAssembler agentPromptAssembler;

    @Mock
    private AgentPreflightCoordinator agentPreflightCoordinator;

    @Mock
    private AgentContextRoutingFacade agentContextRoutingFacade;

    @Mock
    private AgentResultPublisher agentResultPublisher;

    @Mock
    private AgentTaskRuntimeUpdater agentTaskRuntimeUpdater;

    @Mock
    private AgentTaskResultRecorder agentTaskResultRecorder;

    @Mock
    private SessionStyleBindingAppService sessionStyleBindingAppService;

    @Mock
    private IamGateway iamGateway;

    @InjectMocks
    private AgentGenerationWorkflow agentGenerationWorkflow;

    @BeforeEach
    void setUp() {
        lenient().when(iamGateway.findUserByUserId(anyLong()))
                .thenAnswer(invocation -> dirtyWorkPreferenceUser(invocation.getArgument(0), 901L));
        lenient().when(agentModelRoutingService.resolveExecutionConfig(anyLong(), eq(901L), anyString()))
                .thenReturn(executionConfig(901L, "sk-preflight", "dirtywork-agent"));
        lenient().when(agentModelRoutingService.resolveExecutionConfig(anyLong(), eq(66L), anyString()))
                .thenReturn(executionConfig(66L, "sk-exec", "writer-agent"));
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_INITIAL_RUN_SHOULD_USE_TOOL_LOOP_RUNNER() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(11L);
        task.setTaskId(11L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WORLD_BUILD");
        task.setStatus("pending");
        task.setPromptSnapshot("新增世界设定：帝国地理");

        when(agentRepository.findGenerationTask(1L, 11L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(11L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WORLD_BUILD,
                "world-build",
                true,
                false,
                false,
                "需要世界观模式",
                "{\"profile\":\"world-build\"}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(new AgentContextRoutingResult(
                "{\"styleId\":81}",
                StoryBibleContextResult.noop()
        ));
        when(agentPromptAssembler.buildExecutionMessages(eq(task), any(), eq(List.of()), eq("world-build"), eq("")))
                .thenReturn(List.of(Map.of("role", "user", "content", "x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(11L), eq(9L), eq(0L), eq("trace-1"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(77L, 1, ""));

        agentGenerationWorkflow.run(1L, 11L, "trace-1");

        verify(realtimeEventService).publishGenerationStarted(1L, 11L);
        verify(agentToolLoopRunner).execute(eq(1L), eq(11L), eq(9L), eq(0L), eq("trace-1"), any(), any());
        verify(realtimeEventService, never()).publishGenerationWaitingApproval(any(), any(), any(), any());
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_WAITING_APPROVAL_SHOULD_EXPLICITLY_TRANSITION_TASK_STATUS_TO_WAITING_APPROVAL() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(21L);
        task.setTaskId(21L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WORLD_BUILD");
        task.setStatus("pending");
        task.setPromptSnapshot("新增世界设定：边境地图");

        when(agentRepository.findGenerationTask(1L, 21L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(21L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WORLD_BUILD,
                "world-build",
                true,
                false,
                false,
                "需要世界观模式",
                "{\"profile\":\"world-build\"}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(new AgentContextRoutingResult(
                "{\"styleId\":81}",
                StoryBibleContextResult.noop()
        ));
        when(agentPromptAssembler.buildExecutionMessages(eq(task), any(), eq(List.of()), eq("world-build"), eq("")))
                .thenReturn(List.of(Map.of("role", "user", "content", "x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(21L), eq(9L), eq(0L), eq("trace-wait"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(88L, 1, ""));

        agentGenerationWorkflow.run(1L, 21L, "trace-wait");

        org.mockito.InOrder inOrder = inOrder(taskStateMachine, agentRepository, realtimeEventService, agentToolLoopRunner);
        inOrder.verify(taskStateMachine).assertTransition("pending", AgentTaskStatus.RUNNING);
        inOrder.verify(agentRepository).updateGenerationTaskStatus(1L, 21L, AgentTaskStatus.RUNNING.value(), null);
        inOrder.verify(realtimeEventService).publishGenerationStarted(1L, 21L);
        inOrder.verify(agentToolLoopRunner).execute(eq(1L), eq(21L), eq(9L), eq(0L), eq("trace-wait"), any(), any());
        inOrder.verify(taskStateMachine).assertTransition(AgentTaskStatus.RUNNING.value(), AgentTaskStatus.WAITING_APPROVAL);
        inOrder.verify(agentRepository).updateGenerationTaskStatus(1L, 21L, AgentTaskStatus.WAITING_APPROVAL.value(), null);
        verify(realtimeEventService, never()).publishGenerationDone(any(), any(), any());
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_ALLOW_DIRECT_COMPLETION() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(31L);
        task.setTaskId(31L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("直接总结剧情冲突");

        when(agentRepository.findGenerationTask(1L, 31L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(31L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "直接执行",
                "{\"profile\":\"default\"}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(new AgentContextRoutingResult(
                null,
                StoryBibleContextResult.noop()
        ));
        when(agentPromptAssembler.buildExecutionMessages(eq(task), any(), eq(List.of()), eq("default"), eq("")))
                .thenReturn(List.of(Map.of("role", "user", "content", "x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(31L), eq(9L), eq(0L), eq("trace-direct"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed("这是直接完成的答复", 0, ""));
        doAnswer(invocation -> null).when(agentTaskRuntimeUpdater).updateGenerationRuntime(any(), any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentResultPublisher).publishGenerationTokens(any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any());

        agentGenerationWorkflow.run(1L, 31L, "trace-direct");

        verify(agentToolLoopRunner).execute(eq(1L), eq(31L), eq(9L), eq(0L), eq("trace-direct"), any(), any());
        verify(agentTaskResultRecorder).recordAssistantResult(eq(task), eq("这是直接完成的答复"));
        verify(agentResultPublisher).publishGenerationTokens(eq(1L), eq(31L), eq("这是直接完成的答复"), eq("trace-direct"));
        verify(realtimeEventService).publishGenerationDone(1L, 31L, AgentTaskStatus.DONE.value());
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_PASS_STYLE_FROM_SESSION_BINDING_LOOKUP() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(32L);
        task.setTaskId(32L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WORLD_BUILD");
        task.setStatus("pending");
        task.setPromptSnapshot("补完城市背景");

        when(sessionStyleBindingAppService.getBoundStyleSnapshotJson(1L, 9L)).thenReturn("{\"styleId\":81,\"label\":\"史诗感\"}");
        when(agentRepository.findGenerationTask(1L, 32L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(32L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WORLD_BUILD,
                "rewrite",
                true,
                false,
                false,
                "改写且需要风格",
                "{\"profile\":\"rewrite\"}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(new AgentContextRoutingResult(
                "{\"styleId\":81,\"label\":\"史诗感\"}",
                StoryBibleContextResult.noop()
        ));
        when(agentPromptAssembler.buildExecutionMessages(eq(task), any(), eq(List.of()), eq("rewrite"), eq("")))
                .thenReturn(List.of(Map.of(
                        "role", "user",
                        "content", "<context type=\"style\">\n{\"styleId\":81,\"label\":\"史诗感\"}\n</context>\n\n<user_request>\n补完城市背景\n</user_request>"
                )));
        when(agentToolLoopRunner.execute(eq(1L), eq(32L), eq(9L), eq(0L), eq("trace-plain"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(66L, 1, ""));

        agentGenerationWorkflow.run(1L, 32L, "trace-plain");

        ArgumentCaptor<AgentTaskContext> contextCaptor = ArgumentCaptor.forClass(AgentTaskContext.class);
        verify(agentPromptAssembler).buildExecutionMessages(eq(task), contextCaptor.capture(), eq(List.of()), eq("rewrite"), eq(""));
        assertThat(contextCaptor.getValue()).isNotNull();
        assertThat(contextCaptor.getValue().getStyleSnapshotJson()).isEqualTo("{\"styleId\":81,\"label\":\"史诗感\"}");
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_RESUME_AFTER_APPROVAL_SHOULD_CONTINUE_LOOP() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(12L);
        task.setTaskId(12L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("waiting_approval");
        task.setPromptSnapshot("继续写上一轮批准后的内容");

        when(agentRepository.findGenerationTask(1L, 12L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(12L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "恢复执行",
                "{\"profile\":\"default\"}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(new AgentContextRoutingResult(
                null,
                StoryBibleContextResult.noop()
        ));
        when(agentPromptAssembler.buildExecutionMessages(eq(task), any(), eq(List.of()), eq("default"), eq("")))
                .thenReturn(List.of(Map.of("role", "user", "content", "x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(12L), eq(9L), eq(0L), eq("trace-2"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed("续写片段", 1, "tool-context"));
        doAnswer(invocation -> null).when(agentTaskRuntimeUpdater).updateGenerationRuntime(any(), any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentResultPublisher).publishGenerationTokens(any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any());

        agentGenerationWorkflow.runAfterApproval(1L, 12L, "trace-2");

        verify(realtimeEventService).publishGenerationStarted(1L, 12L);
        verify(agentTaskResultRecorder).recordAssistantResult(eq(task), eq("续写片段"));
        verify(agentResultPublisher).publishGenerationTokens(eq(1L), eq(12L), eq("续写片段"), eq("trace-2"));
        verify(realtimeEventService).publishGenerationDone(1L, 12L, AgentTaskStatus.DONE.value());
        verify(agentToolLoopRunner).execute(eq(1L), eq(12L), eq(9L), eq(0L), eq("trace-2"), any(), any());
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_USE_TASK_ID_INSTEAD_OF_PHYSICAL_ID_FOR_STATUS_TRANSITION() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(999L);
        task.setTaskId(41L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("验证 taskId 流转");

        when(agentRepository.findGenerationTask(1L, 41L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(41L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "常规执行",
                "{\"profile\":\"default\"}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(new AgentContextRoutingResult(
                null,
                StoryBibleContextResult.noop()
        ));
        when(agentPromptAssembler.buildExecutionMessages(eq(task), any(), eq(List.of()), eq("default"), eq("")))
                .thenReturn(List.of(Map.of("role", "user", "content", "x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(41L), eq(9L), eq(0L), eq("trace-task-id"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(101L, 1, ""));

        agentGenerationWorkflow.run(1L, 41L, "trace-task-id");

        verify(agentRepository).updateGenerationTaskStatus(1L, 41L, AgentTaskStatus.RUNNING.value(), null);
        verify(agentRepository).updateGenerationTaskStatus(1L, 41L, AgentTaskStatus.WAITING_APPROVAL.value(), null);
        verify(agentRepository, never()).updateGenerationTaskStatus(eq(1L), eq(999L), any(), any());
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_NOT_FORCE_RAG_RETRIEVAL_BEFORE_TOOL_LOOP() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(61L);
        task.setTaskId(61L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("仅在 agent 主动决定时再查询知识库");

        when(agentRepository.findGenerationTask(1L, 61L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(61L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                true,
                "开启 story bible 路由",
                "{\"profile\":\"default\",\"storyBible\":true}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(new AgentContextRoutingResult(
                null,
                StoryBibleContextResult.noop()
        ));
        when(agentPromptAssembler.buildExecutionMessages(eq(task), any(), eq(List.of()), eq("default"), eq("")))
                .thenReturn(List.of(Map.of("role", "user", "content", "x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(61L), eq(9L), eq(0L), eq("trace-rag-tool"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(77L, 1, ""));

        agentGenerationWorkflow.run(1L, 61L, "trace-rag-tool");

        verifyNoInteractions(ragRetrievalService);
        verify(agentContextRoutingFacade).route(any(AgentContextRoutingRequest.class));
        verify(agentPromptAssembler).buildExecutionMessages(eq(task), any(), eq(List.of()), eq("default"), eq(""));
        verify(agentToolLoopRunner).execute(eq(1L), eq(61L), eq(9L), eq(0L), eq("trace-rag-tool"), any(), any());
        verifyNoMoreInteractions(ragRetrievalService);
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_FAIL_FAST_WHEN_LOADED_TASK_HAS_NULL_TASK_ID() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(501L);
        task.setTaskId(null);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("触发空 taskId");

        when(agentRepository.findGenerationTask(1L, 51L)).thenReturn(task);

        agentGenerationWorkflow.run(1L, 51L, "trace-null-task-id");

        verify(agentRepository, never()).updateGenerationTaskStatus(eq(1L), any(), any(), any());
        verify(realtimeEventService, never()).publishGenerationStarted(any(), any());
        verify(realtimeEventService, never()).publishGenerationFailed(any(), any(), any(), any());
        verifyNoInteractions(ragRetrievalService, agentToolLoopRunner, agentTaskRuntimeUpdater, agentResultPublisher, agentTaskResultRecorder);
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_RESOLVE_PREFLIGHT_EXECUTION_CONFIG_BEFORE_TOOL_LOOP() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(71L);
        task.setTaskId(71L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("请先判断再执行");

        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.REWRITE,
                "rewrite",
                false,
                false,
                false,
                "先改写模式",
                "{\"profile\":\"rewrite\"}"
        );

        AgentLlmExecutionConfig preflightExecutionConfig = AgentLlmExecutionConfig.builder()
                .modelConfigId(901L)
                .providerCode("openai-compatible")
                .baseUrl("https://example.com/v1")
                .apiKey("sk-preflight")
                .modelName("dirtywork-agent")
                .keySource("MODEL_CONFIG")
                .build();
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder()
                .modelConfigId(66L)
                .providerCode("openai-compatible")
                .baseUrl("https://example.com/v1")
                .apiKey("sk-exec")
                .modelName("writer-agent")
                .keySource("MODEL_CONFIG")
                .build();

        IamUser iamUser = new IamUser();
        iamUser.setUserId(1001L);
        iamUser.setDirtyWorkAgentModelConfigId(901L);

        when(agentRepository.findGenerationTask(1L, 71L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(71L), any(), any())).thenReturn(1);
        when(iamGateway.findUserByUserId(1001L)).thenReturn(iamUser);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 901L, "trace-preflight")).thenReturn(preflightExecutionConfig);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 66L, "trace-preflight")).thenReturn(executionConfig);
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(new AgentContextRoutingResult(null, StoryBibleContextResult.noop()));
        when(agentPromptAssembler.buildExecutionMessages(eq(task), any(), eq(List.of()), eq("rewrite"), eq("")))
                .thenReturn(List.of(Map.of("role", "user", "content", "x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(71L), eq(9L), eq(0L), eq("trace-preflight"), any(), eq(executionConfig)))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(1L, 1, ""));

        agentGenerationWorkflow.run(1L, 71L, "trace-preflight");

        ArgumentCaptor<com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightRequest> preflightRequestCaptor = ArgumentCaptor.forClass(com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightRequest.class);
        org.mockito.InOrder inOrder = inOrder(agentPreflightCoordinator, agentContextRoutingFacade, agentPromptAssembler, agentToolLoopRunner);
        inOrder.verify(agentPreflightCoordinator).coordinate(preflightRequestCaptor.capture());
        inOrder.verify(agentContextRoutingFacade).route(any());
        inOrder.verify(agentPromptAssembler).buildExecutionMessages(eq(task), any(), eq(List.of()), eq("rewrite"), eq(""));
        inOrder.verify(agentToolLoopRunner).execute(eq(1L), eq(71L), eq(9L), eq(0L), eq("trace-preflight"), any(), eq(executionConfig));
        assertThat(preflightRequestCaptor.getValue().executionConfig()).isEqualTo(preflightExecutionConfig);
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_FAIL_TASK_WHEN_PREFLIGHT_THROWS() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(72L);
        task.setTaskId(72L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("触发 preflight 失败");

        when(agentRepository.findGenerationTask(1L, 72L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(72L), any(), any())).thenReturn(1);
        when(agentPreflightCoordinator.coordinate(any())).thenThrow(new IllegalStateException("preflight failed"));

        agentGenerationWorkflow.run(1L, 72L, "trace-preflight-fail");

        verify(agentRepository).updateGenerationTaskStatus(1L, 72L, AgentTaskStatus.RUNNING.value(), null);
        verify(agentRepository).updateGenerationTaskStatus(1L, 72L, AgentTaskStatus.FAILED.value(), "preflight failed");
        verify(realtimeEventService).publishGenerationFailed(1L, 72L, "AGENT_MODEL_CALL_FAILED", "preflight failed");
        verifyNoInteractions(agentToolLoopRunner);
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_FAIL_TASK_WITH_CLEAR_REASON_WHEN_PROMPT_SNAPSHOT_IS_BLANK() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(73L);
        task.setTaskId(73L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("   \n\t  ");

        when(agentRepository.findGenerationTask(1L, 73L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(73L), any(), any())).thenReturn(1);

        agentGenerationWorkflow.run(1L, 73L, "trace-blank-prompt");

        verify(agentRepository).updateGenerationTaskStatus(1L, 73L, AgentTaskStatus.RUNNING.value(), null);
        verify(agentRepository).updateGenerationTaskStatus(eq(1L), eq(73L), eq(AgentTaskStatus.FAILED.value()), eq("task promptSnapshot must not be blank before preflight"));
        verify(realtimeEventService).publishGenerationFailed(1L, 73L, "AGENT_MODEL_CALL_FAILED", "task promptSnapshot must not be blank before preflight");
        verifyNoInteractions(agentPreflightCoordinator, agentContextRoutingFacade, agentPromptAssembler, agentToolLoopRunner);
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_RESUME_SHOULD_USE_PERSISTED_TASK_CONTEXT_STYLE_SNAPSHOT_INSTEAD_OF_CURRENT_SESSION_STYLE() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(74L);
        task.setTaskId(74L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("waiting_approval");
        task.setPromptSnapshot("恢复后继续执行");

        AgentTaskContext persistedContext = AgentTaskContext.runningOf(904L, 74L, AgentTaskStatus.WAITING_APPROVAL.value(), 3001L, "冻结的选中文本");
        persistedContext.setStyleSnapshotJson("{\"styleId\":81,\"label\":\"冻结风格\"}");

        when(agentRepository.findGenerationTask(1L, 74L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(74L), any(), any())).thenReturn(1);
        when(agentRepository.findTaskContext(74L)).thenReturn(persistedContext);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "恢复执行",
                "{\"profile\":\"default\"}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(new AgentContextRoutingResult(
                "{\"styleId\":81,\"label\":\"冻结风格\"}",
                StoryBibleContextResult.noop()
        ));
        when(agentPromptAssembler.buildExecutionMessages(eq(task), any(), eq(List.of()), eq("default"), eq("")))
                .thenReturn(List.of(Map.of("role", "user", "content", "x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(74L), eq(9L), eq(0L), eq("trace-resume-style"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(1L, 1, ""));

        agentGenerationWorkflow.runAfterApproval(1L, 74L, "trace-resume-style");

        ArgumentCaptor<AgentTaskContext> contextCaptor = ArgumentCaptor.forClass(AgentTaskContext.class);
        verify(agentPromptAssembler).buildExecutionMessages(eq(task), contextCaptor.capture(), eq(List.of()), eq("default"), eq(""));
        assertThat(contextCaptor.getValue().getStyleSnapshotJson()).isEqualTo("{\"styleId\":81,\"label\":\"冻结风格\"}");
        verify(sessionStyleBindingAppService, never()).getBoundStyleSnapshotJson(1L, 9L);
    }

    private static IamUser dirtyWorkPreferenceUser(Long userId, Long dirtyWorkModelConfigId) {
        IamUser iamUser = new IamUser();
        iamUser.setUserId(userId);
        iamUser.setDirtyWorkAgentModelConfigId(dirtyWorkModelConfigId);
        return iamUser;
    }

    private static AgentLlmExecutionConfig executionConfig(Long modelConfigId, String apiKey, String modelName) {
        return AgentLlmExecutionConfig.builder()
                .modelConfigId(modelConfigId)
                .providerCode("openai-compatible")
                .baseUrl("https://example.com/v1")
                .apiKey(apiKey)
                .modelName(modelName)
                .keySource("MODEL_CONFIG")
                .build();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to set field: " + fieldName, ex);
        }
    }
}
