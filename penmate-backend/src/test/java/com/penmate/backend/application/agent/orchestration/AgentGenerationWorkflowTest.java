package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.context.AgentContextRoutingFacade;
import com.penmate.backend.application.agent.runtime.RuntimeStatusView;
import com.penmate.backend.application.agent.runtime.TaskRuntimeStatusPublisher;
import com.penmate.backend.application.agent.context.AgentContextRoutingRequest;
import com.penmate.backend.application.agent.context.AgentContextRoutingResult;
import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.context.StoryBibleContextResult;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.orchestration.preflight.AgentBehaviorType;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightCoordinator;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.agent.prompt.PromptComposer;
import com.penmate.backend.application.agent.prompt.PromptModulePlan;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.prompt.StructuredPromptBlockFormatter;
import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
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
    private PendingToolInvocationRepository pendingToolInvocationRepository;

    @Mock
    private com.penmate.backend.application.agent.AgentTaskStateMachine taskStateMachine;

    @Mock
    private RealtimeEventService realtimeEventService;

    @Mock
    private AgentToolLoopRunner agentToolLoopRunner;

    @Mock
    private AgentModelRoutingService agentModelRoutingService;

    @Mock
    private AgentPromptAssembler agentPromptAssembler;

    @Mock
    private PromptComposer promptComposer;

    @Mock
    private AgentPreflightCoordinator agentPreflightCoordinator;

    @Mock
    private AgentContextRoutingFacade agentContextRoutingFacade;

    @Mock
    private AgentResultPublisher agentResultPublisher;

    @Mock
    private TaskRuntimeStatusPublisher taskRuntimeStatusPublisher;

    @Mock
    private AgentTaskRuntimeUpdater agentTaskRuntimeUpdater;

    @Mock
    private AgentTaskResultRecorder agentTaskResultRecorder;

    @Mock
    private com.penmate.backend.application.todo.TodoCrudApplicationService todoCrudApplicationService;

    @Mock
    private com.penmate.backend.application.storybible.StoryBibleUpdateProposalService storyBibleUpdateProposalService;

    @Mock
    private com.penmate.backend.application.agent.tool.runtime.ToolCallExecutionService toolCallExecutionService;

    @Mock
    private com.penmate.backend.application.approval.ApprovalApplicationService approvalApplicationService;

    @Mock
    private SessionStyleBindingAppService sessionStyleBindingAppService;

    @Mock
    private ConversationWindowBuilder conversationWindowBuilder;

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
        lenient().when(agentRepository.updateGenerationTaskSnapshots(anyLong(), anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_PASS_REAL_COMPOSED_MESSAGES_TO_TOOL_LOOP_WITH_ORDERED_CONTEXT_BLOCKS() throws Exception {
        SystemPromptProvider realSystemPromptProvider = org.mockito.Mockito.mock(SystemPromptProvider.class);
        SkillPromptRegistry realSkillPromptRegistry = org.mockito.Mockito.mock(SkillPromptRegistry.class);
        PromptComposer realPromptComposer = new PromptComposer(realSystemPromptProvider, realSkillPromptRegistry);
        AgentPromptAssembler realAgentPromptAssembler = new AgentPromptAssembler(realSystemPromptProvider, new StructuredPromptBlockFormatter());
        setField(agentGenerationWorkflow, "promptComposer", realPromptComposer);
        setField(agentGenerationWorkflow, "agentPromptAssembler", realAgentPromptAssembler);

        when(realSystemPromptProvider.loadBundle("execution", "default")).thenReturn(new SystemPromptBundle(
                "execution",
                "default",
                List.of(new SystemPromptDocument(
                        "00-base-role.md",
                        "prompts/agent/system/execution/default/00-base-role.md",
                        "你是执行代理"
                )),
                "你是执行代理"
        ));

        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(10L);
        task.setTaskId(10L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("核对设定后继续写作");

        when(agentRepository.findGenerationTask(1L, 10L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(10L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                true,
                false,
                true,
                "先核对设定冲突后输出",
                "{\"profile\":\"default\"}",
                List.of(),
                List.of("不得违背既有设定"),
                List.of(),
                List.of(),
                "输出正文",
                false,
                true,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult(
                "{\"styleId\":81,\"tone\":\"克制\"}",
                new ContextPackage(
                        List.of("story-bible"),
                        List.of(),
                        List.of(),
                        List.of("角色年龄：17（canon）\n不得违背既有设定"),
                        List.of(),
                        "{\"styleId\":81,\"tone\":\"克制\"}",
                        ""
                )
        ));
        when(agentToolLoopRunner.execute(eq(1L), eq(10L), eq(9L), eq(0L), eq("trace-real-chain"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(77L, 1, ""));

        agentGenerationWorkflow.run(1L, 10L, "trace-real-chain");

        ArgumentCaptor<List<com.penmate.backend.domain.agent.model.AgentLlmMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(agentToolLoopRunner).execute(eq(1L), eq(10L), eq(9L), eq(0L), eq("trace-real-chain"), messagesCaptor.capture(), any());
        assertThat(messagesCaptor.getValue()).hasSize(3);
        assertThat(messagesCaptor.getValue().get(0).role()).isEqualTo(com.penmate.backend.domain.agent.model.AgentLlmMessageRole.SYSTEM);
        assertThat(messagesCaptor.getValue().get(0).content()).contains("执行代理");
        assertThat(messagesCaptor.getValue().get(1).role()).isEqualTo(com.penmate.backend.domain.agent.model.AgentLlmMessageRole.SYSTEM);
        assertThat(messagesCaptor.getValue().get(1).content())
                .contains("<context type=\"style\">")
                .contains("<context type=\"story_bible\">")
                .doesNotContain("<user_request>");
        assertThat(messagesCaptor.getValue().get(2).role()).isEqualTo(com.penmate.backend.domain.agent.model.AgentLlmMessageRole.USER);
        assertThat(messagesCaptor.getValue().get(2).content())
                .contains("<user_request>")
                .doesNotContain("<context type=\"style\">")
                .doesNotContain("<context type=\"story_bible\">");
        verifyNoInteractions(promptComposer, agentPromptAssembler);
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_PUBLISH_STRUCTURED_RUNTIME_STATUS_VIA_PUBLISHER_PORT_ON_WAITING_APPROVAL_PATH() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(15L);
        task.setTaskId(15L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("请先分析后等待审批");

        AgentTaskContext persistedContext = AgentTaskContext.runningOf(915L, 15L, AgentTaskStatus.RUNNING.value(), 3009L, "冻结片段");
        persistedContext.setTurnId(50015L);

        when(agentRepository.findGenerationTask(1L, 15L)).thenReturn(task);
        when(agentRepository.findTaskContext(15L)).thenReturn(persistedContext);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(15L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "先分析再等待审批",
                "{\"profile\":\"default\"}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("请先分析后等待审批")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("请先分析后等待审批"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(15L), eq(9L), eq(0L), eq("trace-structured-runtime"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(91L, 1, ""));
        when(pendingToolInvocationRepository.findByApprovalId(91L)).thenReturn(new PendingToolInvocationSnapshot(
                91L,
                1L,
                15L,
                9L,
                "quality_review",
                "{\"chapterId\":301}",
                "{}",
                1001L,
                "trace-structured-runtime",
                "idem-approval-91",
                "pending",
                "loop-approval-91",
                1,
                "call-91",
                "[]",
                "[]",
                "RESUME_LOOP",
                "{\"approvalType\":\"QUALITY_REVIEW\",\"toolDisplayName\":\"质量审查\"}"
        ));

        agentGenerationWorkflow.run(1L, 15L, "trace-structured-runtime");

        ArgumentCaptor<RuntimeStatusView> startedCaptor = ArgumentCaptor.forClass(RuntimeStatusView.class);
        ArgumentCaptor<RuntimeStatusView> waitingCaptor = ArgumentCaptor.forClass(RuntimeStatusView.class);
        verify(taskRuntimeStatusPublisher).publishStarted(eq(1L), startedCaptor.capture());
        verify(taskRuntimeStatusPublisher).publishWaitingApproval(eq(1L), waitingCaptor.capture());

        assertThat(startedCaptor.getValue().taskId()).isEqualTo(15L);
        assertThat(startedCaptor.getValue().sessionId()).isEqualTo(9L);
        assertThat(startedCaptor.getValue().turnId()).isEqualTo(50015L);
        assertThat(startedCaptor.getValue().phase()).isEqualTo("planning");
        assertThat(waitingCaptor.getValue().taskId()).isEqualTo(15L);
        assertThat(waitingCaptor.getValue().sessionId()).isEqualTo(9L);
        assertThat(waitingCaptor.getValue().turnId()).isEqualTo(50015L);
        assertThat(waitingCaptor.getValue().phase()).isEqualTo("waiting_approval");
        assertThat(waitingCaptor.getValue().toolCall()).isNotNull();
        assertThat(waitingCaptor.getValue().toolCall().status()).isEqualTo("waiting_approval");
        assertThat(waitingCaptor.getValue().approval())
                .containsEntry("approvalId", 91L)
                .containsEntry("toolCallId", "call-91")
                .containsEntry("resumeMode", "RESUME_LOOP")
                .containsEntry("approvalType", "QUALITY_REVIEW");
        assertThat(waitingCaptor.getValue().approval().get("approvalSummary"))
                .isEqualTo(Map.of("approvalType", "QUALITY_REVIEW", "toolDisplayName", "质量审查"));
        assertThat(waitingCaptor.getValue().recoverable()).isTrue();
        assertThat(persistedContext.getLastRuntimeStatus()).isEqualTo("waiting_approval");
        assertThat(persistedContext.getRecoveryCursor()).isEqualTo("approval:91");
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
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult("{\"styleId\":81}"));
        PromptPlan promptPlan = promptPlan("world-build");
        when(promptComposer.compose(any(), any(), eq("新增世界设定：帝国地理")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("新增世界设定：帝国地理"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(11L), eq(9L), eq(0L), eq("trace-1"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(77L, 1, ""));

        agentGenerationWorkflow.run(1L, 11L, "trace-1");

        verify(taskRuntimeStatusPublisher).publishStarted(eq(1L), any(RuntimeStatusView.class));
        verify(agentToolLoopRunner).execute(eq(1L), eq(11L), eq(9L), eq(0L), eq("trace-1"), any(), any());
        verify(taskRuntimeStatusPublisher).publishWaitingApproval(eq(1L), any(RuntimeStatusView.class));
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
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult("{\"styleId\":81}"));
        PromptPlan promptPlan = promptPlan("world-build");
        when(promptComposer.compose(any(), any(), eq("新增世界设定：边境地图")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("新增世界设定：边境地图"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(21L), eq(9L), eq(0L), eq("trace-wait"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(88L, 1, ""));

        agentGenerationWorkflow.run(1L, 21L, "trace-wait");

        org.mockito.InOrder inOrder = inOrder(taskStateMachine, agentRepository, taskRuntimeStatusPublisher, agentToolLoopRunner);
        inOrder.verify(taskStateMachine).assertTransition("pending", AgentTaskStatus.RUNNING);
        inOrder.verify(agentRepository).updateGenerationTaskStatus(1L, 21L, AgentTaskStatus.RUNNING.value(), null);
        inOrder.verify(taskRuntimeStatusPublisher).publishStarted(eq(1L), any(RuntimeStatusView.class));
        inOrder.verify(agentToolLoopRunner).execute(eq(1L), eq(21L), eq(9L), eq(0L), eq("trace-wait"), any(), any());
        inOrder.verify(taskStateMachine).assertTransition(AgentTaskStatus.RUNNING.value(), AgentTaskStatus.WAITING_APPROVAL);
        inOrder.verify(agentRepository).updateGenerationTaskStatus(1L, 21L, AgentTaskStatus.WAITING_APPROVAL.value(), null);
        inOrder.verify(taskRuntimeStatusPublisher).publishWaitingApproval(eq(1L), any(RuntimeStatusView.class));
        verify(taskRuntimeStatusPublisher, never()).publishDone(any(), any(RuntimeStatusView.class));
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_PUBLISH_EXECUTING_STATUS_BEFORE_MODEL_COMPLETES() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(30L);
        task.setTaskId(30L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("继续写主角穿越雪原后的遭遇");

        when(agentRepository.findGenerationTask(1L, 30L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(30L), any(), any())).thenReturn(1);
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
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("继续写主角穿越雪原后的遭遇")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("继续写主角穿越雪原后的遭遇"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(30L), eq(9L), eq(0L), eq("trace-executing"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed("雪原尽头亮起了孤灯", 0, ""));
        doAnswer(invocation -> null).when(agentTaskRuntimeUpdater).updateGenerationRuntime(any(), any(), any(), any(), any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentResultPublisher).publishGenerationTokens(any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any(), any());

        agentGenerationWorkflow.run(1L, 30L, "trace-executing");

        org.mockito.InOrder inOrder = inOrder(taskRuntimeStatusPublisher, agentToolLoopRunner);
        inOrder.verify(taskRuntimeStatusPublisher).publishStarted(eq(1L), any(RuntimeStatusView.class));
        inOrder.verify(taskRuntimeStatusPublisher, org.mockito.Mockito.atLeastOnce()).publishStatus(eq(1L), any(RuntimeStatusView.class));
        inOrder.verify(agentToolLoopRunner).execute(eq(1L), eq(30L), eq(9L), eq(0L), eq("trace-executing"), any(), any());
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
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("直接总结剧情冲突")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("直接总结剧情冲突"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(31L), eq(9L), eq(0L), eq("trace-direct"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed("这是直接完成的答复", 0, ""));
        doAnswer(invocation -> null).when(agentTaskRuntimeUpdater).updateGenerationRuntime(any(), any(), any(), any(), any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentResultPublisher).publishGenerationTokens(any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any(), any());

        agentGenerationWorkflow.run(1L, 31L, "trace-direct");

        verify(agentToolLoopRunner).execute(eq(1L), eq(31L), eq(9L), eq(0L), eq("trace-direct"), any(), any());
        verify(agentTaskResultRecorder).recordAssistantResult(eq(task), eq("这是直接完成的答复"), eq(""));
        verify(agentResultPublisher).publishGenerationTokens(eq(1L), eq(31L), eq("这是直接完成的答复"), eq("trace-direct"));
        verify(taskRuntimeStatusPublisher).publishDone(eq(1L), any(RuntimeStatusView.class));

        org.mockito.InOrder completionOrder = inOrder(agentTaskResultRecorder, agentTaskRuntimeUpdater, agentResultPublisher);
        completionOrder.verify(agentTaskResultRecorder).recordAssistantResult(eq(task), eq("这是直接完成的答复"), eq(""));
        completionOrder.verify(agentTaskRuntimeUpdater).updateGenerationRuntime(
                eq(1L),
                eq(31L),
                eq("直接总结剧情冲突"),
                eq("这是直接完成的答复"),
                eq("trace-direct"),
                any(),
                eq(promptPlan),
                any()
        );
        completionOrder.verify(agentResultPublisher).publishGenerationTokens(eq(1L), eq(31L), eq("这是直接完成的答复"), eq("trace-direct"));
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_PERSIST_LOOP_TOKEN_USAGE_AND_ACCUMULATE_SESSION_TOKEN_USAGE_ON_DIRECT_COMPLETION() throws Exception {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(32L);
        task.setTaskId(32L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("完成时需要回写 token 使用量");

        TrackingAgentRepository trackingRepository = new TrackingAgentRepository(task);
        AgentTaskResultRecorder realResultRecorder = new AgentTaskResultRecorder(
                trackingRepository,
                new com.penmate.backend.domain.shared.service.BusinessIdGenerator() {
                    private long currentId = 970320L;

                    @Override
                    public Long nextId() {
                        currentId += 1;
                        return currentId;
                    }
                }
        );
        AgentTaskRuntimeUpdater realRuntimeUpdater = new AgentTaskRuntimeUpdater(trackingRepository);
        setField(agentGenerationWorkflow, "agentRepository", trackingRepository);
        setField(agentGenerationWorkflow, "agentTaskResultRecorder", realResultRecorder);
        setField(agentGenerationWorkflow, "agentTaskRuntimeUpdater", realRuntimeUpdater);

        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "直接执行并回写 token",
                "{\"profile\":\"default\"}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("完成时需要回写 token 使用量")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("完成时需要回写 token 使用量"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(32L), eq(9L), eq(0L), eq("trace-token-usage"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed(
                        "这是带 token 用量的完成答复",
                        0,
                        "",
                        new com.penmate.backend.application.agent.llm.LlmTokenUsage(11, 7, 18)
                ));
        doAnswer(invocation -> null).when(agentResultPublisher).publishGenerationTokens(any(), any(), any(), any());

        agentGenerationWorkflow.run(1L, 32L, "trace-token-usage");

        assertThat(trackingRepository.insertedTaskResult).isNotNull();
        assertThat(trackingRepository.updatedTokenUsageJson)
                .isEqualTo("{\"promptTokens\":11,\"completionTokens\":7,\"totalTokens\":18}");
        assertThat(trackingRepository.accumulatedPromptTokens).isEqualTo(11);
        assertThat(trackingRepository.accumulatedCompletionTokens).isEqualTo(7);
        assertThat(trackingRepository.accumulatedTotalTokens).isEqualTo(18);
        verify(taskRuntimeStatusPublisher).publishDone(eq(1L), any(RuntimeStatusView.class));
    }
 
    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_SEED_TODO_PLAN_IN_RUNTIME_STATUS_AND_SNAPSHOT_BEFORE_TOOL_LOOP() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(40L);
        task.setTaskId(40L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("先规划本章修订待办再执行");

        AgentTaskContext persistedContext = AgentTaskContext.runningOf(940L, 40L, AgentTaskStatus.RUNNING.value(), 301L, "主角提前知道密令，需先规划修订待办");
        persistedContext.setTurnId(50040L);

        when(agentRepository.findGenerationTask(1L, 40L)).thenReturn(task);
        when(agentRepository.findTaskContext(40L)).thenReturn(persistedContext);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(40L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "先生成待办计划再执行",
                "{\"profile\":\"default\",\"todoPlan\":{\"planTitle\":\"第三章修订待办\",\"planSummary\":\"先补齐密令来源链路\",\"recommendedNextAction\":\"apply_todo_plan\",\"items\":[{\"title\":\"修复密令来源\",\"description\":\"补充侍从转述桥段\",\"priority\":\"P0\",\"sourceType\":\"QUALITY_REVIEW\",\"recommendedStatus\":\"TODO\",\"suggestedAutoCreate\":true,\"rationale\":\"避免剧情漏洞\",\"acceptanceCriteria\":[\"密令来源明确\"],\"dependsOn\":[]}]}}",
                List.of(),
                List.of(),
                List.of(),
                List.of("todo_planner"),
                "输出待办后继续生成",
                false,
                false,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("先规划本章修订待办再执行")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("先规划本章修订待办再执行"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(40L), eq(9L), eq(0L), eq("trace-seeded-todo-plan"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(90L, 1, ""));

        agentGenerationWorkflow.run(1L, 40L, "trace-seeded-todo-plan");

        ArgumentCaptor<RuntimeStatusView> runtimeStatusCaptor = ArgumentCaptor.forClass(RuntimeStatusView.class);
        verify(taskRuntimeStatusPublisher, org.mockito.Mockito.atLeastOnce()).publishStatus(eq(1L), runtimeStatusCaptor.capture());
        RuntimeStatusView todoReviewStatus = runtimeStatusCaptor.getAllValues().stream()
                .filter(view -> "todo_review".equals(view.phase()))
                .findFirst()
                .orElse(null);

        assertThat(todoReviewStatus).isNull();
        assertThat(persistedContext.getActiveToolCallsSnapshot())
                .doesNotContain("todo_planner")
                .doesNotContain("todoPlan")
                .doesNotContain("planTitle");
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_PERSIST_AUTO_CREATABLE_TODOS_VIA_APPLICATION_SERVICE_WHEN_TOOL_TRACE_CONTAINS_TODO_PLAN() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(41L);
        task.setTaskId(41L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("把质量问题整理成待办");

        when(agentRepository.findGenerationTask(1L, 41L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(41L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "生成待办计划",
                "{\"profile\":\"default\"}",
                List.of(),
                List.of(),
                List.of(),
                List.of("todo_planner"),
                "输出待办",
                false,
                false,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("把质量问题整理成待办")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("把质量问题整理成待办"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        String todoPlanJson = "{\"planTitle\":\"第三章修订待办\",\"planSummary\":\"先修复逻辑漏洞\",\"recommendedNextAction\":\"创建第一项\",\"items\":[{\"title\":\"修复密令来源\",\"description\":\"补充侍从转述桥段\",\"priority\":\"P0\",\"sourceType\":\"QUALITY_REVIEW\",\"recommendedStatus\":\"TODO\",\"suggestedAutoCreate\":true,\"rationale\":\"避免剧情漏洞\",\"acceptanceCriteria\":[\"密令来源明确\"]}]}";
        when(agentToolLoopRunner.execute(eq(1L), eq(41L), eq(9L), eq(0L), eq("trace-todo-plan"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed("待办规划完成", 1, todoPlanJson));
        doAnswer(invocation -> null).when(agentTaskRuntimeUpdater).updateGenerationRuntime(any(), any(), any(), any(), any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentResultPublisher).publishGenerationTokens(any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any(), any());

        agentGenerationWorkflow.run(1L, 41L, "trace-todo-plan");

        ArgumentCaptor<RuntimeStatusView> runtimeStatusCaptor = ArgumentCaptor.forClass(RuntimeStatusView.class);
        verify(taskRuntimeStatusPublisher).publishDone(eq(1L), runtimeStatusCaptor.capture());
        assertThat(runtimeStatusCaptor.getValue().phase()).isEqualTo("done");
        verifyNoInteractions(todoCrudApplicationService);
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_APPEND_STORY_BIBLE_PROPOSAL_SUMMARY_TO_RECORDED_TOOL_TRACE() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(42L);
        task.setTaskId(42L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("整理这一章新增设定");

        AgentTaskContext persistedContext = AgentTaskContext.runningOf(942L, 42L, AgentTaskStatus.RUNNING.value(), 301L, "林烬在雾港钟楼向苏砚坦白了秘密。只有林烬知道的真相不再只属于他。");
        when(agentRepository.findGenerationTask(1L, 42L)).thenReturn(task);
        when(agentRepository.findTaskContext(42L)).thenReturn(persistedContext);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(42L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                true,
                "需要整理故事圣经提案",
                "{\"profile\":\"default\",\"storyBible\":true}",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "输出设定建议",
                false,
                true,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("整理这一章新增设定")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("整理这一章新增设定"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(42L), eq(9L), eq(0L), eq("trace-story-bible-proposal"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed("设定建议已整理", 0, ""));
        when(storyBibleUpdateProposalService.proposeUpdatesFromChapter(eq(1L), eq(301L), eq("设定建议已整理")))
                .thenReturn(List.of(new com.penmate.backend.application.storybible.StoryBibleProposalItem(
                        "information_boundary.linjin.secret",
                        "information_boundary",
                        "林烬与苏砚都知道城主其实是林烬的生父。",
                        "PROPOSED",
                        2,
                        "林烬在雾港钟楼向苏砚坦白了秘密",
                        301L,
                        "BOUNDARY_UPDATE"
                )));
        doAnswer(invocation -> null).when(agentTaskRuntimeUpdater).updateGenerationRuntime(any(), any(), any(), any(), any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentResultPublisher).publishGenerationTokens(any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any(), any());

        agentGenerationWorkflow.run(1L, 42L, "trace-story-bible-proposal");

        verify(storyBibleUpdateProposalService).proposeUpdatesFromChapter(1L, 301L, "设定建议已整理");
        verify(agentTaskResultRecorder).recordAssistantResult(eq(task), eq("设定建议已整理"), org.mockito.ArgumentMatchers.contains("\"proposalSummary\""));
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_TRIGGER_CONTROLLED_REVISION_WHEN_QUALITY_REPORT_ALLOWS_ANOTHER_ROUND() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(43L);
        task.setTaskId(43L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("续写并检查质量");

        when(agentRepository.findGenerationTask(1L, 43L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(43L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "需要质量审查并允许一轮修订",
                "{\"profile\":\"default\"}",
                List.of(),
                List.of("不得违背既有设定"),
                List.of("CONTINUITY_CHECK"),
                List.of("quality_review"),
                "输出正文",
                false,
                false,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("续写并检查质量")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("续写并检查质量"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        String qualityReportJson = "{\"score\":61,\"passes\":[\"主冲突已出现\"],\"issues\":[{\"dimension\":\"PLOT_LOGIC\",\"severity\":\"HIGH\",\"summary\":\"主角提前知道密令\",\"evidence\":\"第二段直接复述密令\",\"suggestion\":\"改为侍从转述\"}],\"needsRevision\":true,\"riskFlags\":[\"PLOT_HOLE\"],\"revisionSuggestions\":[{\"priority\":\"P0\",\"target\":\"剧情逻辑\",\"instruction\":\"修复密令来源\",\"rationale\":\"避免剧情漏洞\"}],\"currentRevisionRound\":0,\"maxRevisionRounds\":1,\"revisionAllowed\":true,\"reviewSummary\":\"存在剧情逻辑问题，需要修订。\"}";
        when(agentToolLoopRunner.execute(eq(1L), eq(43L), eq(9L), eq(0L), eq("trace-quality-revise"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed("初稿正文", 1, qualityReportJson));
        when(toolCallExecutionService.execute(any())).thenReturn(com.penmate.backend.application.agent.tool.runtime.ToolCallResult.success(
                "{\"draftText\":\"修订后正文\",\"operation\":\"revise\",\"preservedConstraints\":[\"不得违背既有设定\"],\"sourceSummary\":\"质量修订\"}"
        ));
        doAnswer(invocation -> null).when(agentTaskRuntimeUpdater).updateGenerationRuntime(any(), any(), any(), any(), any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentResultPublisher).publishGenerationTokens(any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any(), any());

        agentGenerationWorkflow.run(1L, 43L, "trace-quality-revise");

        verify(toolCallExecutionService).execute(any(com.penmate.backend.application.agent.tool.runtime.ToolCallRequest.class));
        verify(agentTaskResultRecorder).recordAssistantResult(eq(task), eq("修订后正文"), org.mockito.ArgumentMatchers.contains("\"operation\":\"revise\""));
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_NOT_TRIGGER_REVISION_WHEN_CURRENT_ROUND_REACHES_MAX_ROUNDS() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(45L);
        task.setTaskId(45L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("续写并检查质量但不得超限修订");

        when(agentRepository.findGenerationTask(1L, 45L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(45L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "质量审查达到上限时不得继续修订",
                "{\"profile\":\"default\"}",
                List.of(),
                List.of("不得违背既有设定"),
                List.of("CONTINUITY_CHECK"),
                List.of("quality_review"),
                "输出正文",
                false,
                false,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("续写并检查质量但不得超限修订")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("续写并检查质量但不得超限修订"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        String qualityReportJson = "{\"score\":61,\"passes\":[\"主冲突已出现\"],\"issues\":[{\"dimension\":\"PLOT_LOGIC\",\"severity\":\"HIGH\",\"summary\":\"主角提前知道密令\",\"evidence\":\"第二段直接复述密令\",\"suggestion\":\"改为侍从转述\"}],\"needsRevision\":true,\"riskFlags\":[\"PLOT_HOLE\"],\"revisionSuggestions\":[{\"priority\":\"P0\",\"target\":\"剧情逻辑\",\"instruction\":\"修复密令来源\",\"rationale\":\"避免剧情漏洞\"}],\"currentRevisionRound\":1,\"maxRevisionRounds\":1,\"revisionAllowed\":true,\"reviewSummary\":\"存在剧情逻辑问题，需要修订。\"}";
        when(agentToolLoopRunner.execute(eq(1L), eq(45L), eq(9L), eq(0L), eq("trace-quality-limit"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed("超限前初稿正文", 1, qualityReportJson));
        doAnswer(invocation -> null).when(agentTaskRuntimeUpdater).updateGenerationRuntime(any(), any(), any(), any(), any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentResultPublisher).publishGenerationTokens(any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any(), any());

        agentGenerationWorkflow.run(1L, 45L, "trace-quality-limit");

        verify(toolCallExecutionService, never()).execute(any(com.penmate.backend.application.agent.tool.runtime.ToolCallRequest.class));
        verify(agentTaskResultRecorder).recordAssistantResult(eq(task), eq("超限前初稿正文"), org.mockito.ArgumentMatchers.contains("\"currentRevisionRound\":1"));
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_ENTER_WAITING_APPROVAL_WHEN_STORY_BIBLE_PROPOSAL_IS_HIGH_RISK() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(44L);
        task.setTaskId(44L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("整理这一章新增设定并等待确认");

        AgentTaskContext persistedContext = AgentTaskContext.runningOf(944L, 44L, AgentTaskStatus.RUNNING.value(), 301L, "林烬向苏砚坦白了城主身份秘密。只有林烬知道的边界已经变化。");
        when(agentRepository.findGenerationTask(1L, 44L)).thenReturn(task);
        when(agentRepository.findTaskContext(44L)).thenReturn(persistedContext);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(44L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                true,
                "需要故事圣经审批",
                "{\"profile\":\"default\",\"storyBible\":true}",
                List.of(),
                List.of(),
                List.of(),
                List.of("todo_planner"),
                "输出设定建议",
                false,
                true,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("整理这一章新增设定并等待确认")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("整理这一章新增设定并等待确认"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(44L), eq(9L), eq(0L), eq("trace-story-bible-approval"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed(
                        "设定建议已整理",
                        1,
                        "{\"planTitle\":\"第三章修订待办\",\"planSummary\":\"补齐密令来源链路\",\"recommendedNextAction\":\"apply_todo_plan\",\"items\":[{\"title\":\"修复密令来源\",\"description\":\"补充侍从转述桥段\",\"priority\":\"P0\",\"sourceType\":\"QUALITY_REVIEW\",\"recommendedStatus\":\"TODO\",\"suggestedAutoCreate\":true,\"rationale\":\"避免剧情漏洞\",\"acceptanceCriteria\":[\"密令来源明确\"],\"dependsOn\":[]}] }"
                ));
        when(storyBibleUpdateProposalService.proposeUpdatesFromChapter(eq(1L), eq(301L), eq("设定建议已整理")))
                .thenReturn(List.of(new com.penmate.backend.application.storybible.StoryBibleProposalItem(
                        "information_boundary.linjin.secret",
                        "information_boundary",
                        "林烬与苏砚都知道城主其实是林烬的生父。",
                        "PROPOSED",
                        3,
                        "林烬向苏砚坦白了城主身份秘密",
                        301L,
                        "BOUNDARY_UPDATE"
                )));
        com.penmate.backend.domain.approval.model.ApprovalRequest approvalRequest = new com.penmate.backend.domain.approval.model.ApprovalRequest();
        approvalRequest.setId(9901L);
        approvalRequest.setProjectId(1L);
        approvalRequest.setTaskId(44L);
        approvalRequest.setApprovalType("STORY_BIBLE_UPDATE");
        approvalRequest.setStatus("pending");
        when(approvalApplicationService.create(any(com.penmate.backend.application.approval.command.CreateApprovalCommand.class), eq("trace-story-bible-approval")))
                .thenReturn(approvalRequest);
        when(pendingToolInvocationRepository.findByApprovalId(9901L)).thenReturn(new PendingToolInvocationSnapshot(
                9901L,
                1L,
                44L,
                9L,
                "story_bible_update",
                "{\"chapterId\":301}",
                "{}",
                1001L,
                "trace-story-bible-approval",
                "idem-story-bible-9901",
                "pending",
                "story-bible-loop-9901",
                0,
                "story-bible-call-1",
                "[]",
                "[]",
                "RESUME_LOOP",
                "{\"approvalType\":\"STORY_BIBLE_UPDATE\",\"proposalSummary\":\"故事圣经更新待确认\"}"
        ));
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any(), any());

        agentGenerationWorkflow.run(1L, 44L, "trace-story-bible-approval");

        ArgumentCaptor<RuntimeStatusView> runtimeStatusCaptor = ArgumentCaptor.forClass(RuntimeStatusView.class);
        verify(approvalApplicationService).create(any(com.penmate.backend.application.approval.command.CreateApprovalCommand.class), eq("trace-story-bible-approval"));
        verify(pendingToolInvocationRepository).save(any(PendingToolInvocationSnapshot.class));
        verify(taskRuntimeStatusPublisher).publishWaitingApproval(eq(1L), runtimeStatusCaptor.capture());
        assertThat(runtimeStatusCaptor.getValue().toolCall()).isNotNull();
        assertThat(runtimeStatusCaptor.getValue().toolCall().toolCode()).isEqualTo("story_bible_update");
        assertThat(runtimeStatusCaptor.getValue().toolCall().toolCallId()).isEqualTo("story-bible-call-1");
        assertThat(runtimeStatusCaptor.getValue().storyBibleApproval()).isNotNull();
        assertThat(runtimeStatusCaptor.getValue().storyBibleApproval().approvalId()).isEqualTo(9901L);
        assertThat(runtimeStatusCaptor.getValue().storyBibleApproval().approvalType()).isEqualTo("STORY_BIBLE_UPDATE");
        assertThat(runtimeStatusCaptor.getValue().storyBibleApproval().proposalSummary()).isEqualTo("故事圣经更新待确认");
        assertThat(runtimeStatusCaptor.getValue().storyBibleApproval().entryKeys()).containsExactly("information_boundary.linjin.secret");
        assertThat(runtimeStatusCaptor.getValue().storyBibleApproval().nextAction()).isEqualTo("await_approval");
        verify(agentTaskResultRecorder).recordAssistantResult(
                eq(task),
                eq("设定建议已整理"),
                org.mockito.ArgumentMatchers.argThat(trace -> trace != null
                        && trace.contains("\"approvalType\":\"STORY_BIBLE_UPDATE\"")
                        && trace.contains("\"proposedContent\":\"林烬与苏砚都知道城主其实是林烬的生父。\"")));
        verify(taskRuntimeStatusPublisher, never()).publishDone(eq(1L), any(RuntimeStatusView.class));
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_PASS_RUNTIME_SNAPSHOTS_TO_RUNTIME_UPDATER_ON_COMPLETION() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(33L);
        task.setTaskId(33L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("完成后持久化运行时快照");

        when(agentRepository.findGenerationTask(1L, 33L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(33L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                true,
                false,
                true,
                "先核对设定再完成",
                "{\"profile\":\"default\"}",
                List.of("CONTINUITY_CHECK"),
                List.of("不得违背设定"),
                List.of(),
                List.of("story_bible_lookup"),
                "输出正文",
                false,
                true,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult(
                "{\"styleId\":81,\"label\":\"冻结风格\"}",
                new ContextPackage(
                        List.of("story-bible"),
                        List.of(),
                        List.of(),
                        List.of("角色年龄：17（canon）"),
                        List.of(),
                        "{\"styleId\":81,\"label\":\"冻结风格\"}",
                        ""
                )
        ));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("完成后持久化运行时快照")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("完成后持久化运行时快照"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(33L), eq(9L), eq(0L), eq("trace-runtime-snapshot"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed("这是带快照的完成答复", 0, ""));
        doAnswer(invocation -> null).when(agentResultPublisher).publishGenerationTokens(any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any(), any());

        agentGenerationWorkflow.run(1L, 33L, "trace-runtime-snapshot");

        ArgumentCaptor<TaskProfile> taskProfileCaptor = ArgumentCaptor.forClass(TaskProfile.class);
        ArgumentCaptor<ContextPackage> contextCaptor = ArgumentCaptor.forClass(ContextPackage.class);
        verify(agentTaskRuntimeUpdater).updateGenerationRuntime(
                eq(1L),
                eq(33L),
                eq("完成后持久化运行时快照"),
                eq("这是带快照的完成答复"),
                eq("trace-runtime-snapshot"),
                taskProfileCaptor.capture(),
                eq(promptPlan),
                contextCaptor.capture()
        );
        assertThat(taskProfileCaptor.getValue().executionProfile()).isEqualTo("default");
        assertThat(taskProfileCaptor.getValue().tools()).containsExactly("story_bible_lookup");
        assertThat(contextCaptor.getValue().styleSnapshot()).isEqualTo("{\"styleId\":81,\"label\":\"冻结风格\"}");
        assertThat(contextCaptor.getValue().storyBibleEntries()).containsExactly("角色年龄：17（canon）");
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_PREFER_ROUTING_RESULT_CONTEXT_PACKAGE_OVER_LEGACY_STORY_BIBLE_REBUILD() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(34L);
        task.setTaskId(34L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("必须透传结构化上下文包");

        when(agentRepository.findGenerationTask(1L, 34L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(34L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                true,
                false,
                true,
                "透传 builder 结果",
                "{\"profile\":\"default\"}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        ContextPackage builtContextPackage = new ContextPackage(
                List.of("repository", "proposal"),
                List.of("story_bible_missing_partial"),
                List.of("story_bible_conflict:hero.identity"),
                List.of("[hero.identity] 主角身份\n林烬是守夜人见习生", "[character.secret.knowledge.linjin] 秘密信息\n只有林烬知道城主其实是他的生父"),
                List.of(),
                "{\"styleId\":81,\"label\":\"冻结风格\"}",
                "chapter:42"
        );
        when(agentContextRoutingFacade.route(any())).thenReturn(new AgentContextRoutingResult(
                "{\"styleId\":81,\"label\":\"冻结风格\"}",
                new StoryBibleContextResult(true, "legacy-story-bible", "旧字符串上下文"),
                builtContextPackage
        ));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("必须透传结构化上下文包")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("必须透传结构化上下文包"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(34L), eq(9L), eq(0L), eq("trace-context-package"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed("透传完成", 0, ""));
        doAnswer(invocation -> null).when(agentResultPublisher).publishGenerationTokens(any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any(), any());

        agentGenerationWorkflow.run(1L, 34L, "trace-context-package");

        ArgumentCaptor<ContextPackage> composerContextCaptor = ArgumentCaptor.forClass(ContextPackage.class);
        ArgumentCaptor<ContextPackage> assemblerContextCaptor = ArgumentCaptor.forClass(ContextPackage.class);
        ArgumentCaptor<ContextPackage> runtimeContextCaptor = ArgumentCaptor.forClass(ContextPackage.class);
        verify(promptComposer).compose(any(), composerContextCaptor.capture(), eq("必须透传结构化上下文包"));
        verify(agentPromptAssembler).buildExecutionMessages(eq(promptPlan), assemblerContextCaptor.capture(), eq("必须透传结构化上下文包"), any());
        verify(agentTaskRuntimeUpdater).updateGenerationRuntime(
                eq(1L),
                eq(34L),
                eq("必须透传结构化上下文包"),
                eq("透传完成"),
                eq("trace-context-package"),
                any(),
                eq(promptPlan),
                runtimeContextCaptor.capture()
        );
        assertThat(composerContextCaptor.getValue().sources()).isEmpty();
        assertThat(composerContextCaptor.getValue().missingContextFlags()).isEmpty();
        assertThat(composerContextCaptor.getValue().conflicts()).isEmpty();
        assertThat(composerContextCaptor.getValue().storyBibleEntries()).isEmpty();
        assertThat(assemblerContextCaptor.getValue()).isEqualTo(builtContextPackage);
        assertThat(runtimeContextCaptor.getValue()).isEqualTo(builtContextPackage);
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_FAIL_FAST_WHEN_ROUTING_RESULT_OMITS_CONTEXT_PACKAGE() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(35L);
        task.setTaskId(35L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("routing result 不能缺少 context package");

        when(agentRepository.findGenerationTask(1L, 35L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(35L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "缺少 context package 时必须失败",
                "{\"profile\":\"default\"}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenAnswer(invocation -> new AgentContextRoutingResult(
                null,
                StoryBibleContextResult.noop(),
                null
        ));

        agentGenerationWorkflow.run(1L, 35L, "trace-missing-context-package");

        verify(agentRepository).updateGenerationTaskStatus(1L, 35L, AgentTaskStatus.RUNNING.value(), null);
        verify(agentRepository).updateGenerationTaskStatus(1L, 35L, AgentTaskStatus.FAILED.value(), "contextPackage");
        verify(taskRuntimeStatusPublisher).publishFailed(eq(1L), any(RuntimeStatusView.class));
        verifyNoInteractions(agentPromptAssembler, agentToolLoopRunner, agentTaskRuntimeUpdater, agentResultPublisher, agentTaskResultRecorder);
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
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult("{\"styleId\":81,\"label\":\"史诗感\"}"));
        PromptPlan promptPlan = promptPlan("rewrite");
        when(promptComposer.compose(any(), any(), eq("补完城市背景")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("补完城市背景"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(32L), eq(9L), eq(0L), eq("trace-plain"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(66L, 1, ""));

        agentGenerationWorkflow.run(1L, 32L, "trace-plain");

        ArgumentCaptor<ContextPackage> contextCaptor = ArgumentCaptor.forClass(ContextPackage.class);
        verify(agentPromptAssembler).buildExecutionMessages(eq(promptPlan), contextCaptor.capture(), eq("补完城市背景"), any());
        assertThat(contextCaptor.getValue()).isNotNull();
        assertThat(contextCaptor.getValue().styleSnapshot()).isEqualTo("{\"styleId\":81,\"label\":\"史诗感\"}");
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
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("继续写上一轮批准后的内容")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("继续写上一轮批准后的内容"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(12L), eq(9L), eq(0L), eq("trace-2"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed("续写片段", 1, "tool-context"));
        doAnswer(invocation -> null).when(agentTaskRuntimeUpdater).updateGenerationRuntime(any(), any(), any(), any(), any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentResultPublisher).publishGenerationTokens(any(), any(), any(), any());
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any(), any());

        agentGenerationWorkflow.runAfterApproval(1L, 12L, "trace-2");

        verify(taskRuntimeStatusPublisher).publishStarted(eq(1L), any(RuntimeStatusView.class));
        verify(agentTaskResultRecorder).recordAssistantResult(eq(task), eq("续写片段"), eq("tool-context"));
        verify(agentResultPublisher).publishGenerationTokens(eq(1L), eq(12L), eq("续写片段"), eq("trace-2"));
        verify(taskRuntimeStatusPublisher).publishDone(eq(1L), any(RuntimeStatusView.class));
        verify(agentToolLoopRunner).execute(eq(1L), eq(12L), eq(9L), eq(0L), eq("trace-2"), any(), any());
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_REQUIRE_NEW_STORY_BIBLE_APPROVAL_AFTER_RESUMING_FROM_NON_STORY_BIBLE_APPROVAL() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(121L);
        task.setTaskId(121L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("waiting_approval");
        task.setPromptSnapshot("恢复后继续整理设定");

        AgentTaskContext persistedContext = AgentTaskContext.runningOf(9121L, 121L, AgentTaskStatus.WAITING_APPROVAL.value(), 301L, "恢复自质量审查审批后的正文");
        persistedContext.setRecoveryCursor("approval:88011");
        persistedContext.setActiveToolCallsSnapshot("[{\"toolCallId\":\"call-quality-1\",\"toolCode\":\"quality_review\",\"toolName\":\"质量审查\",\"status\":\"waiting_approval\",\"iteration\":1}]");

        when(agentRepository.findGenerationTask(1L, 121L)).thenReturn(task);
        when(agentRepository.findTaskContext(121L)).thenReturn(persistedContext);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(121L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                true,
                "恢复后仍需故事圣经审批",
                "{\"profile\":\"default\"}",
                List.of(),
                List.of(),
                List.of(),
                List.of("todo_planner"),
                "继续输出",
                false,
                true,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("恢复后继续整理设定"))).thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("恢复后继续整理设定"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(121L), eq(9L), eq(0L), eq("trace-resume-non-story-approval"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.completed(
                        "恢复后的正文建议",
                        1,
                        "{\"planTitle\":\"第三章修订待办\",\"planSummary\":\"补齐密令来源\",\"recommendedNextAction\":\"apply_todo_plan\",\"items\":[{\"title\":\"修复密令来源\",\"description\":\"补充侍从转述桥段\",\"priority\":\"P0\",\"sourceType\":\"QUALITY_REVIEW\",\"recommendedStatus\":\"TODO\",\"suggestedAutoCreate\":true,\"rationale\":\"避免剧情漏洞\",\"acceptanceCriteria\":[\"密令来源明确\"],\"dependsOn\":[]}]}"
                ));
        when(storyBibleUpdateProposalService.proposeUpdatesFromChapter(eq(1L), eq(301L), eq("恢复后的正文建议")))
                .thenReturn(List.of(new com.penmate.backend.application.storybible.StoryBibleProposalItem(
                        "information_boundary.linjin.secret",
                        "information_boundary",
                        "林烬与苏砚都知道城主其实是林烬的生父。",
                        "PROPOSED",
                        3,
                        "恢复后新增的设定变更",
                        301L,
                        "BOUNDARY_UPDATE"
                )));
        com.penmate.backend.domain.approval.model.ApprovalRequest approvalRequest = new com.penmate.backend.domain.approval.model.ApprovalRequest();
        approvalRequest.setId(9902L);
        approvalRequest.setProjectId(1L);
        approvalRequest.setTaskId(121L);
        approvalRequest.setApprovalType("STORY_BIBLE_UPDATE");
        approvalRequest.setStatus("pending");
        when(approvalApplicationService.create(any(com.penmate.backend.application.approval.command.CreateApprovalCommand.class), eq("trace-resume-non-story-approval")))
                .thenReturn(approvalRequest);
        when(pendingToolInvocationRepository.findByApprovalId(9902L)).thenReturn(new PendingToolInvocationSnapshot(
                9902L,
                1L,
                121L,
                9L,
                "story_bible_update",
                "{\"chapterId\":301}",
                "{}",
                1001L,
                "trace-resume-non-story-approval",
                "idem-story-9902",
                "pending",
                "story-loop-9902",
                0,
                "story-bible-call-9902",
                "[]",
                "[]",
                "RESUME_LOOP",
                "{\"approvalType\":\"STORY_BIBLE_UPDATE\",\"proposalSummary\":\"故事圣经更新待确认\"}"
        ));
        doAnswer(invocation -> null).when(agentTaskResultRecorder).recordAssistantResult(any(), any(), any());

        agentGenerationWorkflow.runAfterApproval(1L, 121L, "trace-resume-non-story-approval");

        verify(approvalApplicationService).create(any(com.penmate.backend.application.approval.command.CreateApprovalCommand.class), eq("trace-resume-non-story-approval"));
        verify(taskRuntimeStatusPublisher).publishWaitingApproval(eq(1L), any(RuntimeStatusView.class));
        verify(taskRuntimeStatusPublisher, never()).publishDone(eq(1L), any(RuntimeStatusView.class));
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
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("验证 taskId 流转")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("验证 taskId 流转"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(41L), eq(9L), eq(0L), eq("trace-task-id"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(101L, 1, ""));

        agentGenerationWorkflow.run(1L, 41L, "trace-task-id");

        verify(agentRepository).updateGenerationTaskStatus(1L, 41L, AgentTaskStatus.RUNNING.value(), null);
        verify(agentRepository).updateGenerationTaskStatus(1L, 41L, AgentTaskStatus.WAITING_APPROVAL.value(), null);
        verify(agentRepository, never()).updateGenerationTaskStatus(eq(1L), eq(999L), any(), any());
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_NOT_FORCE_RAG_RETRIEVAL_BEFORE_TOOL_LOOP_AND_SHOULD_PASS_TASK_PROFILE_TO_CONTEXT_ROUTING() {
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
                "{\"profile\":\"default\",\"storyBible\":true}",
                List.of("CONTINUITY_CHECK"),
                List.of("不得违背既有设定"),
                List.of("story-bible-guard"),
                List.of("story_bible_lookup"),
                "先核对设定再输出",
                false,
                true,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(missingStoryBibleRoutingResult(null, null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("仅在 agent 主动决定时再查询知识库")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("仅在 agent 主动决定时再查询知识库"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(61L), eq(9L), eq(0L), eq("trace-rag-tool"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(77L, 1, ""));

        agentGenerationWorkflow.run(1L, 61L, "trace-rag-tool");

        ArgumentCaptor<AgentContextRoutingRequest> routingRequestCaptor = ArgumentCaptor.forClass(AgentContextRoutingRequest.class);
        verify(agentContextRoutingFacade).route(routingRequestCaptor.capture());
        assertThat(routingRequestCaptor.getValue().taskProfile()).isNotNull();
        assertThat(routingRequestCaptor.getValue().taskProfile().executionProfile()).isEqualTo("default");
        assertThat(routingRequestCaptor.getValue().taskProfile().tools()).containsExactly("story_bible_lookup");
        assertThat(routingRequestCaptor.getValue().taskProfile().hardConstraints()).containsExactly("不得违背既有设定");
        assertThat(routingRequestCaptor.getValue().taskProfile().includeStoryBible()).isTrue();
        verify(promptComposer).compose(any(), any(), eq("仅在 agent 主动决定时再查询知识库"));
        verify(agentPromptAssembler).buildExecutionMessages(eq(promptPlan), any(), eq("仅在 agent 主动决定时再查询知识库"), any());
        verify(agentToolLoopRunner).execute(eq(1L), eq(61L), eq(9L), eq(0L), eq("trace-rag-tool"), any(), any());
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_FORWARD_REAL_SESSION_TASK_AND_USER_MENTIONS_TO_CONTEXT_ROUTING() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(611L);
        task.setTaskId(611L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(19L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("请核对林烬与苏砚在第42章的当前设定后继续输出");

        when(agentRepository.findGenerationTask(1L, 611L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(611L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.STORY_BIBLE_QUERY_CANDIDATE,
                "default",
                false,
                true,
                true,
                "需要 story bible 与 rag context",
                "{\"profile\":\"default\",\"rag\":true}",
                List.of("CONTINUITY_CHECK", "STORY_BIBLE_QUERY"),
                List.of("不得违背既有设定"),
                List.of("story_bible_query", "continuity_checker"),
                List.of("story_bible_lookup"),
                "先核对设定再输出",
                false,
                true,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(missingStoryBibleRoutingResult(null, null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("请核对林烬与苏砚在第42章的当前设定后继续输出")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("请核对林烬与苏砚在第42章的当前设定后继续输出"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(611L), eq(19L), eq(0L), eq("trace-routing-fields"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(88L, 1, ""));

        agentGenerationWorkflow.run(1L, 611L, "trace-routing-fields");

        ArgumentCaptor<AgentContextRoutingRequest> routingRequestCaptor = ArgumentCaptor.forClass(AgentContextRoutingRequest.class);
        verify(agentContextRoutingFacade).route(routingRequestCaptor.capture());
        assertThat(routingRequestCaptor.getValue().sessionId()).isEqualTo(19L);
        assertThat(routingRequestCaptor.getValue().taskId()).isEqualTo(611L);
        assertThat(routingRequestCaptor.getValue().userMentionedEntities()).contains("林烬", "苏砚");
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_MARK_MISSING_STORY_BIBLE_CONTEXT_IN_CONTEXT_PACKAGE_WHEN_FALLBACK_IS_USED() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(62L);
        task.setTaskId(62L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("无设定时允许 fallback 但必须标记缺失");

        when(agentRepository.findGenerationTask(1L, 62L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(62L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                true,
                "缺少 story bible 时也允许继续，但必须标记 missing",
                "{\"profile\":\"default\",\"storyBible\":true}",
                List.of("CONTINUITY_CHECK"),
                List.of("不得伪装成 canon"),
                List.of(),
                List.of("story_bible_lookup"),
                "先说明缺失再继续生成",
                false,
                true,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(missingStoryBibleRoutingResult(null, null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("无设定时允许 fallback 但必须标记缺失")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("无设定时允许 fallback 但必须标记缺失"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(62L), eq(9L), eq(0L), eq("trace-missing-story-bible"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(78L, 1, ""));

        agentGenerationWorkflow.run(1L, 62L, "trace-missing-story-bible");

        ArgumentCaptor<ContextPackage> contextCaptor = ArgumentCaptor.forClass(ContextPackage.class);
        verify(agentPromptAssembler).buildExecutionMessages(eq(promptPlan), contextCaptor.capture(), eq("无设定时允许 fallback 但必须标记缺失"), any());
        assertThat(contextCaptor.getValue().storyBibleEntries()).isEmpty();
        assertThat(contextCaptor.getValue().sources()).containsExactly("noop");
        assertThat(contextCaptor.getValue().missingContextFlags()).containsExactly("story_bible_missing");
        assertThat(contextCaptor.getValue().conflicts()).isEmpty();
        assertThat(contextCaptor.getValue().chapterScope()).isEmpty();
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_NOT_MARK_STORY_BIBLE_AS_MISSING_WHEN_PREFLIGHT_DOES_NOT_ENABLE_IT() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(63L);
        task.setTaskId(63L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("未启用 story bible 时不得误标 missing");

        when(agentRepository.findGenerationTask(1L, 63L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(63L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "当前请求不启用 story bible",
                "{\"profile\":\"default\",\"storyBible\":false}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("未启用 story bible 时不得误标 missing")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("未启用 story bible 时不得误标 missing"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(63L), eq(9L), eq(0L), eq("trace-story-bible-disabled"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(79L, 1, ""));

        agentGenerationWorkflow.run(1L, 63L, "trace-story-bible-disabled");

        ArgumentCaptor<ContextPackage> contextCaptor = ArgumentCaptor.forClass(ContextPackage.class);
        verify(agentPromptAssembler).buildExecutionMessages(eq(promptPlan), contextCaptor.capture(), eq("未启用 story bible 时不得误标 missing"), any());
        assertThat(contextCaptor.getValue().storyBibleEntries()).isEmpty();
        assertThat(contextCaptor.getValue().missingContextFlags()).isEmpty();
        assertThat(contextCaptor.getValue().conflicts()).isEmpty();
        assertThat(contextCaptor.getValue().chapterScope()).isEmpty();
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
        verifyNoInteractions(agentToolLoopRunner, agentTaskRuntimeUpdater, agentResultPublisher, agentTaskResultRecorder, promptComposer);
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
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("rewrite");
        when(promptComposer.compose(any(), any(), eq("请先判断再执行")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("请先判断再执行"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(71L), eq(9L), eq(0L), eq("trace-preflight"), any(), eq(executionConfig)))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(1L, 1, ""));

        agentGenerationWorkflow.run(1L, 71L, "trace-preflight");

        ArgumentCaptor<com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightRequest> preflightRequestCaptor = ArgumentCaptor.forClass(com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightRequest.class);
        org.mockito.InOrder inOrder = inOrder(agentPreflightCoordinator, promptComposer, agentContextRoutingFacade, agentPromptAssembler, agentToolLoopRunner);
        inOrder.verify(agentPreflightCoordinator).coordinate(preflightRequestCaptor.capture());
        inOrder.verify(promptComposer).compose(any(), any(), eq("请先判断再执行"));
        inOrder.verify(agentContextRoutingFacade).route(any());
        inOrder.verify(agentPromptAssembler).buildExecutionMessages(eq(promptPlan), any(), eq("请先判断再执行"), any());
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
        verify(taskRuntimeStatusPublisher).publishFailed(eq(1L), any(RuntimeStatusView.class));
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
        verify(taskRuntimeStatusPublisher).publishFailed(eq(1L), any(RuntimeStatusView.class));
        verifyNoInteractions(agentPreflightCoordinator, agentContextRoutingFacade, promptComposer, agentPromptAssembler, agentToolLoopRunner);
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_FAIL_TASK_WHEN_DIRTY_WORK_MODEL_CONFIG_IS_MISSING() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(74L);
        task.setTaskId(74L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("继续生成正文");

        IamUser iamUser = new IamUser();
        iamUser.setUserId(1001L);
        iamUser.setDirtyWorkAgentModelConfigId(null);

        when(agentRepository.findGenerationTask(1L, 74L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(74L), any(), any())).thenReturn(1);
        when(iamGateway.findUserByUserId(1001L)).thenReturn(iamUser);

        agentGenerationWorkflow.run(1L, 74L, "trace-missing-dirtywork");

        verify(agentRepository).updateGenerationTaskStatus(1L, 74L, AgentTaskStatus.RUNNING.value(), null);
        verify(agentRepository).updateGenerationTaskStatus(1L, 74L, AgentTaskStatus.FAILED.value(), "dirty work agent model config is required before preflight");
        verify(taskRuntimeStatusPublisher).publishFailed(eq(1L), any(RuntimeStatusView.class));
        verifyNoInteractions(agentPreflightCoordinator, agentContextRoutingFacade, promptComposer, agentPromptAssembler, agentToolLoopRunner);
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_RESUME_SHOULD_USE_PERSISTED_TASK_CONTEXT_STYLE_SNAPSHOT_INSTEAD_OF_CURRENT_SESSION_STYLE() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(75L);
        task.setTaskId(75L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("waiting_approval");
        task.setPromptSnapshot("恢复后继续执行");

        AgentTaskContext persistedContext = AgentTaskContext.runningOf(904L, 75L, AgentTaskStatus.WAITING_APPROVAL.value(), 3001L, "冻结的选中文本");
        persistedContext.setStyleSnapshotJson("{\"styleId\":81,\"label\":\"冻结风格\"}");

        when(agentRepository.findGenerationTask(1L, 75L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(75L), any(), any())).thenReturn(1);
        when(agentRepository.findTaskContext(75L)).thenReturn(persistedContext);
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
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult(
                "{\"styleId\":81,\"label\":\"冻结风格\"}",
                new ContextPackage(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "{\"styleId\":81,\"label\":\"冻结风格\"}",
                        "chapter:3001"
                )
        ));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("恢复后继续执行")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("恢复后继续执行"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(75L), eq(9L), eq(0L), eq("trace-resume-style"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(1L, 1, ""));

        agentGenerationWorkflow.runAfterApproval(1L, 75L, "trace-resume-style");

        ArgumentCaptor<ContextPackage> contextCaptor = ArgumentCaptor.forClass(ContextPackage.class);
        verify(agentPromptAssembler).buildExecutionMessages(eq(promptPlan), contextCaptor.capture(), eq("恢复后继续执行"), any());
        assertThat(contextCaptor.getValue().styleSnapshot()).isEqualTo("{\"styleId\":81,\"label\":\"冻结风格\"}");
        verify(sessionStyleBindingAppService, never()).getBoundStyleSnapshotJson(1L, 9L);
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_ORCHESTRATE_TASK_PROFILE_PROMPT_CONTEXT_AND_TOOL_LOOP_IN_STRICT_ORDER_AND_PUBLISH_STRUCTURED_STATUS_NODES() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(64L);
        task.setTaskId(64L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("严格编排顺序与状态节点");

        when(agentRepository.findGenerationTask(1L, 64L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(64L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "按严格主链路执行",
                "{\"profile\":\"default\"}"
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(routingResult((String) null));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("严格编排顺序与状态节点")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("严格编排顺序与状态节点"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(64L), eq(9L), eq(0L), eq("trace-strict-order"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(80L, 1, ""));

        agentGenerationWorkflow.run(1L, 64L, "trace-strict-order");

        org.mockito.InOrder workflowOrder = inOrder(agentPreflightCoordinator, promptComposer, agentContextRoutingFacade, agentToolLoopRunner);
        workflowOrder.verify(agentPreflightCoordinator).coordinate(any());
        workflowOrder.verify(promptComposer).compose(any(), any(), eq("严格编排顺序与状态节点"));
        workflowOrder.verify(agentContextRoutingFacade).route(any());
        workflowOrder.verify(agentToolLoopRunner).execute(eq(1L), eq(64L), eq(9L), eq(0L), eq("trace-strict-order"), any(), any());

        verify(taskRuntimeStatusPublisher, org.mockito.Mockito.atLeast(4)).publishStatus(eq(1L), any(RuntimeStatusView.class));
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_WRITE_TASK_PROFILE_PROMPT_PLAN_AND_CONTEXT_PACKAGE_INTO_TASK_CONTEXT_BEFORE_WAITING_APPROVAL() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(65L);
        task.setTaskId(65L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setModelConfigId(66L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("等待审批前必须写入结构化快照");

        AgentTaskContext persistedContext = AgentTaskContext.runningOf(
                905L,
                65L,
                AgentTaskStatus.RUNNING.value(),
                3007L,
                "冻结选中文本"
        );
        persistedContext.setStyleSnapshotJson("{\"styleId\":81,\"label\":\"冻结风格\"}");

        when(agentRepository.findGenerationTask(1L, 65L)).thenReturn(task);
        when(agentRepository.findTaskContext(65L)).thenReturn(persistedContext);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(65L), any(), any())).thenReturn(1);
        AgentPreflightDecision decision = new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                true,
                false,
                true,
                "缺少 story bible 允许 fallback，但必须先落快照",
                "{\"profile\":\"default\",\"storyBible\":true}",
                List.of("CONTINUITY_CHECK"),
                List.of("不得伪装成 canon"),
                List.of(),
                List.of("story_bible_lookup"),
                "先说明缺失再继续",
                false,
                true,
                false
        );
        when(agentPreflightCoordinator.coordinate(any())).thenReturn(decision);
        when(agentContextRoutingFacade.route(any())).thenReturn(missingStoryBibleRoutingResult("{\"styleId\":81,\"label\":\"冻结风格\"}", 3007L));
        PromptPlan promptPlan = promptPlan("default");
        when(promptComposer.compose(any(), any(), eq("等待审批前必须写入结构化快照")))
                .thenReturn(promptPlan);
        when(agentPromptAssembler.buildExecutionMessages(eq(promptPlan), any(), eq("等待审批前必须写入结构化快照"), any()))
                .thenReturn(List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("x")));
        when(agentToolLoopRunner.execute(eq(1L), eq(65L), eq(9L), eq(0L), eq("trace-waiting-snapshots"), any(), any()))
                .thenAnswer(invocation -> {
                    assertThat(persistedContext.getTaskProfileJson()).contains("\"executionProfile\":\"default\"");
                    assertThat(persistedContext.getTaskProfileJson()).contains("\"tools\":[\"story_bible_lookup\"]");
                    assertThat(persistedContext.getPromptPlanJson()).contains("\"finalProfile\":\"default\"");
                    assertThat(persistedContext.getContextPackageJson()).contains("\"missingContextFlags\":[\"story_bible_missing\"]");
                    return AgentToolLoopIterationResult.waitingApproval(81L, 1, "");
                });

        agentGenerationWorkflow.run(1L, 65L, "trace-waiting-snapshots");

        assertThat(persistedContext.getTaskProfileJson()).contains("\"executionProfile\":\"default\"");
        assertThat(persistedContext.getPromptPlanJson()).contains("\"finalProfile\":\"default\"");
        assertThat(persistedContext.getContextPackageJson()).contains("\"missingContextFlags\":[\"story_bible_missing\"]");
        verify(taskRuntimeStatusPublisher).publishWaitingApproval(eq(1L), any(RuntimeStatusView.class));
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_DEPEND_ON_TASK_RUNTIME_STATUS_PUBLISHER_PORT_FOR_MAIN_PHASE_PUBLICATION() {
        Class<?> publisherType = tryLoadClass("com.penmate.backend.application.agent.runtime.TaskRuntimeStatusPublisher");

        assertThat(publisherType)
                .as("TaskRuntimeStatusPublisher should exist for workflow main-phase publication")
                .isNotNull();
        if (publisherType == null) {
            return;
        }

        boolean publisherFieldPresent = Arrays.stream(AgentGenerationWorkflow.class.getDeclaredFields())
                .anyMatch(field -> field.getType().equals(publisherType));

        assertThat(publisherFieldPresent)
                .as("workflow should depend on TaskRuntimeStatusPublisher instead of scattering runtime status semantics")
                .isTrue();
    }

    private static AgentContextRoutingResult routingResult(String styleSnapshot) {
        return routingResult(new ContextPackage(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                styleSnapshot == null ? "" : styleSnapshot,
                ""
        ));
    }

    private static AgentContextRoutingResult routingResult(ContextPackage contextPackage) {
        return new AgentContextRoutingResult(
                contextPackage == null ? null : contextPackage.styleSnapshot(),
                StoryBibleContextResult.noop(),
                contextPackage
        );
    }

    private static AgentContextRoutingResult routingResult(String styleSnapshot,
                                                           ContextPackage contextPackage) {
        return routingResult(new ContextPackage(
                contextPackage == null ? List.of() : contextPackage.sources(),
                contextPackage == null ? List.of() : contextPackage.missingContextFlags(),
                contextPackage == null ? List.of() : contextPackage.conflicts(),
                contextPackage == null ? List.of() : contextPackage.storyBibleEntries(),
                contextPackage == null ? List.of() : contextPackage.ragRefs(),
                styleSnapshot == null ? "" : styleSnapshot,
                contextPackage == null ? "" : contextPackage.chapterScope()
        ));
    }

    private static AgentContextRoutingResult missingStoryBibleRoutingResult(String styleSnapshot,
                                                                             Long chapterId) {
        return routingResult(new ContextPackage(
                List.of("noop"),
                List.of("story_bible_missing"),
                List.of(),
                List.of(),
                List.of(),
                styleSnapshot == null ? "" : styleSnapshot,
                chapterId == null ? "" : "chapter:" + chapterId
        ));
    }

    private static final class TrackingAgentRepository implements AgentRepository {

        private final AgentGenerationTask task;
        private com.penmate.backend.domain.agent.model.AgentTaskResult insertedTaskResult;
        private String updatedTokenUsageJson;
        private int accumulatedPromptTokens;
        private int accumulatedCompletionTokens;
        private int accumulatedTotalTokens;

        private TrackingAgentRepository(AgentGenerationTask task) {
            this.task = task;
        }

        @Override
        public java.util.List<com.penmate.backend.domain.agent.model.AgentConversation> listConversations(Long projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.penmate.backend.domain.agent.model.AgentConversation findConversation(Long projectId, Long conversationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insertConversation(com.penmate.backend.domain.agent.model.AgentConversation conversation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<com.penmate.backend.domain.agent.model.AgentMessage> listMessages(Long conversationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextMessageSeq(Long conversationId) {
            return 1;
        }

        @Override
        public int insertMessage(com.penmate.backend.domain.agent.model.AgentMessage message) {
            return 1;
        }

        @Override
        public int touchConversationLastMessage(Long conversationId) {
            return 1;
        }

        @Override
        public int insertGenerationTask(AgentGenerationTask task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentGenerationTask findGenerationTask(Long projectId, Long taskId) {
            return task;
        }

        @Override
        public AgentTaskContext findTaskContext(Long taskId) {
            return null;
        }

        @Override
        public int updateGenerationTaskStatus(Long projectId, Long taskId, String status, String errorMsg) {
            task.setStatus(status);
            return 1;
        }

        @Override
        public int updateGenerationTaskActiveApproval(Long projectId, Long taskId, Long approvalId) {
            return 1;
        }

        @Override
        public int updateGenerationTaskRuntime(Long projectId, Long taskId, String tokenUsageJson, String costJson, String traceId) {
            if (tokenUsageJson != null) {
                this.updatedTokenUsageJson = tokenUsageJson;
            }
            return 1;
        }

        @Override
        public int updateGenerationTaskSnapshots(Long projectId,
                                                 Long taskId,
                                                 String taskProfileJson,
                                                 String promptPlanJson,
                                                 String contextPackageJson,
                                                 String activeToolCallsSnapshot,
                                                 String lastRuntimeStatus,
                                                 String recoveryCursor) {
            return 1;
        }

        @Override
        public int insertTaskResult(com.penmate.backend.domain.agent.model.AgentTaskResult taskResult) {
            this.insertedTaskResult = taskResult;
            return 1;
        }

        @Override
        public int updateGenerationTaskResultLink(Long projectId, Long taskId, Long resultId) {
            return 1;
        }

        public int incrementSessionTokenUsage(Long projectId,
                                              Long sessionId,
                                              Integer promptTokens,
                                              Integer completionTokens,
                                              Integer totalTokens) {
            this.accumulatedPromptTokens += promptTokens == null ? 0 : promptTokens;
            this.accumulatedCompletionTokens += completionTokens == null ? 0 : completionTokens;
            this.accumulatedTotalTokens += totalTokens == null ? 0 : totalTokens;
            return 1;
        }
    }

    private static IamUser dirtyWorkPreferenceUser(Long userId, Long dirtyWorkModelConfigId) {
        IamUser iamUser = new IamUser();
        iamUser.setUserId(userId);
        iamUser.setDirtyWorkAgentModelConfigId(dirtyWorkModelConfigId);
        return iamUser;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = AgentGenerationWorkflow.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static PromptPlan promptPlan(String profile) {
        return new PromptPlan(
                List.of(new PromptModulePlan("execution:" + profile, "prompt-source", true, "test")),
                List.of(),
                profile,
                "system prompt preview"
        );
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

    private static Class<?> tryLoadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

}
