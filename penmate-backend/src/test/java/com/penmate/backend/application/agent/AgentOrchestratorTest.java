package com.penmate.backend.application.agent;

import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.rag.RagRetrievalService;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentTaskStateMachine taskStateMachine;

    @Mock
    private RealtimeEventService realtimeEventService;

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private AgentLlmGateway agentLlmGateway;

    @Mock
    private RagRetrievalService ragRetrievalService;

    @Mock
    private PluginToolCoordinator pluginToolCoordinator;

    @Mock
    private AgentModelRoutingService agentModelRoutingService;

    @InjectMocks
    private AgentOrchestrator agentOrchestrator;

    @Test
    void UT_APP_AGENT_ORCHESTRATOR_WAITING_APPROVAL_FLOW() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(11L);
        task.setProjectId(1L);
        task.setConversationId(9L);
        task.setTaskType("WORLD_BUILD");
        task.setStatus("pending");
        task.setPromptSnapshot("新增世界设定：帝国地理");

        AgentConversation conversation = new AgentConversation();
        conversation.setId(9L);
        conversation.setUserId(1001L);

        when(agentRepository.findGenerationTask(1L, 11L)).thenReturn(task);
        when(agentRepository.findConversation(1L, 9L)).thenReturn(conversation);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(11L), any(), any())).thenReturn(1);
        when(approvalRequestRepository.insert(any())).thenAnswer(invocation -> {
            ApprovalRequest req = invocation.getArgument(0);
            req.setId(77L);
            return 1;
        });

        agentOrchestrator.run(1L, 11L, "trace-1");

        verify(realtimeEventService).publishGenerationStarted(1L, 11L);
        verify(realtimeEventService).publishGenerationWaitingApproval(1L, 11L, 77L, "WORLD_SETTING_CREATE");
        verify(agentLlmGateway, never()).generate(any(), any(), any(), any());
    }

    @Test
    void UT_APP_AGENT_ORCHESTRATOR_RESUME_AFTER_APPROVAL() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(12L);
        task.setProjectId(1L);
        task.setConversationId(9L);
        task.setTaskType("WRITE");
        task.setStatus("waiting_approval");

        when(agentRepository.findGenerationTask(1L, 12L)).thenReturn(task);
        when(agentRepository.updateGenerationTaskStatus(eq(1L), eq(12L), any(), any())).thenReturn(1);
        when(ragRetrievalService.retrieve(eq(1L), eq(12L), any(), eq("trace-2")))
                .thenReturn(new RagRetrievalService.RetrievalResult(java.util.List.of(), 1L));
        when(pluginToolCoordinator.execute(any())).thenReturn(ToolExecutionResult.success("plugin-a", "tool-a", "tool-context"));
        when(agentModelRoutingService.resolveExecutionConfig(1L, null, "trace-2")).thenReturn(null);
        when(agentLlmGateway.generate(eq(task), any(), eq("tool-context"), any())).thenReturn("续写片段");
        when(agentRepository.nextMessageSeq(9L)).thenReturn(3);
        when(agentRepository.insertMessage(any())).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(9L)).thenReturn(1);

        agentOrchestrator.runAfterApproval(1L, 12L, "trace-2");

        verify(realtimeEventService).publishGenerationStarted(1L, 12L);
        verify(realtimeEventService).publishGenerationToken(eq(1L), eq(12L), any(), eq(false));
        verify(realtimeEventService).publishGenerationDone(1L, 12L, AgentTaskStatus.DONE.value());
        verify(approvalRequestRepository, never()).insert(any());
    }
}

