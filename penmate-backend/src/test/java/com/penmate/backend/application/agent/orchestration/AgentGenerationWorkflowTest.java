package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.rag.RagRetrievalService;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private AgentResultPublisher agentResultPublisher;

    @Mock
    private AgentTaskRuntimeUpdater agentTaskRuntimeUpdater;

    @Mock
    private AgentTaskResultRecorder agentTaskResultRecorder;

    @InjectMocks
    private AgentGenerationWorkflow agentGenerationWorkflow;

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_INITIAL_RUN_SHOULD_USE_TOOL_LOOP_RUNNER() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(11L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setConversationId(9L);
        task.setTaskType("WORLD_BUILD");
        task.setStatus("pending");
        task.setPromptSnapshot("新增世界设定：帝国地理");

        when(agentRepository.findGenerationTask(1L, 11L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(11L), any(), any())).thenReturn(1);
        when(ragRetrievalService.retrieve(eq(1L), eq(11L), any(), eq("trace-1")))
                .thenReturn(new RagRetrievalService.RetrievalResult(List.of(), 1L));
        when(agentPromptAssembler.buildInitialMessages(eq(task), any())).thenReturn(List.of(Map.of("role", "user", "content", "x")));
        when(agentModelRoutingService.resolveExecutionConfig(1L, 1001L, "trace-1")).thenReturn(null);
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
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setConversationId(9L);
        task.setTaskType("WORLD_BUILD");
        task.setStatus("pending");
        task.setPromptSnapshot("新增世界设定：边境地图");

        when(agentRepository.findGenerationTask(1L, 21L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(21L), any(), any())).thenReturn(1);
        when(ragRetrievalService.retrieve(eq(1L), eq(21L), any(), eq("trace-wait")))
                .thenReturn(new RagRetrievalService.RetrievalResult(List.of(), 1L));
        when(agentPromptAssembler.buildInitialMessages(eq(task), any())).thenReturn(List.of(Map.of("role", "user", "content", "x")));
        when(agentModelRoutingService.resolveExecutionConfig(1L, 1001L, "trace-wait")).thenReturn(null);
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
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("pending");
        task.setPromptSnapshot("直接总结剧情冲突");
        task.setStyleProfileSnapshot("简洁");

        when(agentRepository.findGenerationTask(1L, 31L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(31L), any(), any())).thenReturn(1);
        when(ragRetrievalService.retrieve(eq(1L), eq(31L), any(), eq("trace-direct")))
                .thenReturn(new RagRetrievalService.RetrievalResult(List.of(), 1L));
        when(agentPromptAssembler.buildInitialMessages(eq(task), any())).thenReturn(List.of(Map.of("role", "user", "content", "x")));
        when(agentModelRoutingService.resolveExecutionConfig(1L, 1001L, "trace-direct")).thenReturn(null);
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
    void UT_APP_AGENT_GENERATION_WORKFLOW_SHOULD_PASS_PLAIN_USER_MESSAGE_TO_LOOP() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(32L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setConversationId(9L);
        task.setTaskType("WORLD_BUILD");
        task.setStatus("pending");
        task.setPromptSnapshot("补完城市背景");
        task.setStyleProfileSnapshot("史诗感");

        List<Map<String, Object>> assembledMessages = List.of(Map.of(
                "role", "user",
                "content", "写作风格约束：\n史诗感\n\n用户指令：\n补完城市背景"
        ));

        when(agentRepository.findGenerationTask(1L, 32L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(32L), any(), any())).thenReturn(1);
        when(ragRetrievalService.retrieve(eq(1L), eq(32L), any(), eq("trace-plain")))
                .thenReturn(new RagRetrievalService.RetrievalResult(List.of(), 1L));
        when(agentPromptAssembler.buildInitialMessages(eq(task), any())).thenReturn(assembledMessages);
        when(agentModelRoutingService.resolveExecutionConfig(1L, 1001L, "trace-plain")).thenReturn(null);
        when(agentToolLoopRunner.execute(eq(1L), eq(32L), eq(9L), eq(0L), eq("trace-plain"), any(), any()))
                .thenReturn(AgentToolLoopIterationResult.waitingApproval(66L, 1, ""));

        agentGenerationWorkflow.run(1L, 32L, "trace-plain");

        org.mockito.ArgumentCaptor<List<Map<String, Object>>> messagesCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(agentToolLoopRunner).execute(eq(1L), eq(32L), eq(9L), eq(0L), eq("trace-plain"), messagesCaptor.capture(), any());
        List<Map<String, Object>> messages = messagesCaptor.getValue();

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).containsEntry("role", "user");
        assertThat(String.valueOf(messages.get(0).get("content")))
                .contains("写作风格约束：")
                .contains("史诗感")
                .contains("用户指令：\n补完城市背景")
                .doesNotContain("context_enhancer")
                .doesNotContain("toolCode");
    }

    @Test
    void UT_APP_AGENT_GENERATION_WORKFLOW_RESUME_AFTER_APPROVAL_SHOULD_CONTINUE_LOOP() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(12L);
        task.setProjectId(1L);
        task.setUserId(1001L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("waiting_approval");

        when(agentRepository.findGenerationTask(1L, 12L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(12L), any(), any())).thenReturn(1);
        when(ragRetrievalService.retrieve(eq(1L), eq(12L), any(), eq("trace-2")))
                .thenReturn(new RagRetrievalService.RetrievalResult(List.of(), 1L));
        when(agentPromptAssembler.buildInitialMessages(eq(task), any())).thenReturn(List.of(Map.of("role", "user", "content", "x")));
        when(agentModelRoutingService.resolveExecutionConfig(1L, 1001L, "trace-2")).thenReturn(null);
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
}
