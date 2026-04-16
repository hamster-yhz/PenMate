package com.penmate.backend.application.agent;

import com.penmate.backend.application.agent.command.AgentCommands.ApplyGenerationCommand;
import com.penmate.backend.application.agent.command.AgentCommands.CreateConversationCommand;
import com.penmate.backend.application.agent.command.AgentCommands.CreateGenerationCommand;
import com.penmate.backend.application.agent.command.AgentCommands.CreateMessageCommand;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private RealtimeEventService realtimeEventService;

    @InjectMocks
    private AgentApplicationService agentApplicationService;

    @Test
    void UT_APP_AGENT_LIST_CONVERSATIONS_SUCCESS() {
        when(agentRepository.listConversations(1L)).thenReturn(List.of(new AgentConversation(), new AgentConversation()));

        List<AgentConversation> result = agentApplicationService.listConversations(1L);

        assertThat(result).hasSize(2);
        verify(agentRepository).listConversations(1L);
        verifyNoInteractions(auditService, realtimeEventService);
    }

    @Test
    void UT_APP_AGENT_CREATE_CONVERSATION_SUCCESS() {
        Long projectId = 1L;
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-AGENT-CONV-CREATE";

        when(agentRepository.insertConversation(any(AgentConversation.class))).thenAnswer(invocation -> {
            AgentConversation conversation = invocation.getArgument(0);
            conversation.setId(77L);
            return 1;
        });

        AgentConversation result = agentApplicationService.createConversation(
                projectId,
                new CreateConversationCommand(2001L, "新会话", "{}", "active", operatorId),
                traceId
        );

        assertThat(result.getId()).isEqualTo(77L);
        verify(agentRepository).insertConversation(any(AgentConversation.class));
        verify(auditService).write(eq(traceId), eq(operatorId), eq("agent"), eq("conversation:create"), eq("agent_conversations"), eq("77"), eq("{}"), eq(201));
        verifyNoInteractions(realtimeEventService);
    }

    @Test
    void UT_APP_AGENT_LIST_MESSAGES_CONVERSATION_NOT_FOUND() {
        when(agentRepository.findConversation(1L, 999L)).thenReturn(null);

        assertThatThrownBy(() -> agentApplicationService.listMessages(1L, 999L))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Conversation not found");
    }

    @Test
    void UT_APP_AGENT_CREATE_MESSAGE_SUCCESS() {
        Long projectId = 1L;
        Long conversationId = 10L;
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-AGENT-MSG-CREATE";

        AgentConversation conversation = new AgentConversation();
        conversation.setId(conversationId);
        when(agentRepository.findConversation(projectId, conversationId)).thenReturn(conversation);
        when(agentRepository.nextMessageSeq(conversationId)).thenReturn(3);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(88L);
            return 1;
        });

        AgentMessage result = agentApplicationService.createMessage(
                projectId,
                conversationId,
                new CreateMessageCommand("user", "text", "hello", null, null, operatorId),
                traceId
        );

        assertThat(result.getId()).isEqualTo(88L);
        verify(agentRepository).touchConversationLastMessage(conversationId);
        verify(auditService).write(eq(traceId), eq(operatorId), eq("agent"), eq("message:create"), eq("agent_messages"), eq("88"), eq("hello"), eq(201));
        verifyNoInteractions(realtimeEventService);
    }

    @Test
    void UT_APP_AGENT_CREATE_GENERATION_SUCCESS() {
        Long projectId = 1L;
        Long conversationId = 10L;
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-AGENT-GEN-CREATE";

        AgentConversation conversation = new AgentConversation();
        conversation.setId(conversationId);
        when(agentRepository.findConversation(projectId, conversationId)).thenReturn(conversation);
        when(agentRepository.insertGenerationTask(any(AgentGenerationTask.class))).thenAnswer(invocation -> {
            AgentGenerationTask task = invocation.getArgument(0);
            task.setId(501L);
            return 1;
        });
        AgentGenerationTask doneTask = new AgentGenerationTask();
        doneTask.setId(501L);
        doneTask.setStatus("done");
        when(agentRepository.findGenerationTask(projectId, 501L)).thenReturn(doneTask);

        AgentGenerationTask result = agentApplicationService.createGeneration(
                projectId,
                new CreateGenerationCommand(conversationId, 20L, "rewrite", "prompt", "style", "plugins", operatorId),
                traceId
        );

        assertThat(result.getId()).isEqualTo(501L);
        verify(realtimeEventService).publishGenerationToken(projectId, 501L, "开始生成", false);
        verify(realtimeEventService).publishGenerationToken(projectId, 501L, "", true);
        verify(agentRepository).updateGenerationTaskStatus(projectId, 501L, "done", null);
        verify(auditService).write(eq(traceId), eq(operatorId), eq("agent"), eq("generation:create"), eq("agent_generation_tasks"), eq("501"), eq("prompt"), eq(201));
    }

    @Test
    void UT_APP_AGENT_APPLY_GENERATION_NOT_READY() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(501L);
        task.setStatus("running");
        when(agentRepository.findGenerationTask(1L, 501L)).thenReturn(task);

        assertThatThrownBy(() -> agentApplicationService.applyGeneration(
                1L,
                501L,
                new ApplyGenerationCommand(1001L, "应用"),
                "trace"
        )).isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Generation task is not ready for apply");
    }

    @Test
    void UT_APP_AGENT_APPLY_GENERATION_SUCCESS() {
        Long projectId = 1L;
        Long taskId = 501L;
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-AGENT-GEN-APPLY";

        AgentGenerationTask readyTask = new AgentGenerationTask();
        readyTask.setId(taskId);
        readyTask.setStatus("done");
        AgentGenerationTask appliedTask = new AgentGenerationTask();
        appliedTask.setId(taskId);
        appliedTask.setStatus("applied");

        when(agentRepository.findGenerationTask(projectId, taskId)).thenReturn(readyTask, appliedTask);
        when(agentRepository.updateGenerationTaskStatus(projectId, taskId, "applied", null)).thenReturn(1);

        AgentGenerationTask result = agentApplicationService.applyGeneration(
                projectId,
                taskId,
                new ApplyGenerationCommand(operatorId, "确认应用"),
                traceId
        );

        assertThat(result.getStatus()).isEqualTo("applied");
        verify(agentRepository).updateGenerationTaskStatus(projectId, taskId, "applied", null);
        verify(auditService).write(eq(traceId), eq(operatorId), eq("agent"), eq("generation:apply"), eq("agent_generation_tasks"), eq("501"), eq("确认应用"), eq(200));
        verifyNoInteractions(realtimeEventService);
    }
}

