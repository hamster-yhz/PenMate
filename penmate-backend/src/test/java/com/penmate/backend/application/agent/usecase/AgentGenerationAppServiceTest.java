package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.command.AgentCommands.CreateGenerationCommand;
import com.penmate.backend.application.agent.orchestration.AgentGenerationWorkflowDispatcher;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.service.AgentTaskTransitionPolicy;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentGenerationAppServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentTaskTransitionPolicy taskTransitionPolicy;

    @Mock
    private AgentGenerationWorkflowDispatcher orchestrationDispatcher;

    @Mock
    private AgentJsonInputNormalizer jsonInputNormalizer;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

    @InjectMocks
    private AgentGenerationAppService agentGenerationAppService;

    @Test
    void UT_APP_AGENT_CREATE_GENERATION_SHOULD_GENERATE_TASK_ID_BEFORE_INSERT() {
        Long projectId = 920001L;
        Long conversationId = 920002L;
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId(conversationId);

        when(agentRepository.findConversation(projectId, conversationId)).thenReturn(conversation);
        when(businessIdGenerator.nextId()).thenReturn(940001L);
        when(jsonInputNormalizer.normalize(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRepository.insertGenerationTask(any(AgentGenerationTask.class))).thenReturn(1);

        AgentGenerationTask storedTask = new AgentGenerationTask();
        storedTask.setTaskId(940001L);
        when(agentRepository.findGenerationTask(projectId, 940001L)).thenReturn(storedTask);

        AgentGenerationTask result = agentGenerationAppService.createGeneration(
                projectId,
                new CreateGenerationCommand(
                        conversationId,
                        3001L,
                        4001L,
                        "WRITE",
                        "prompt-json",
                        "style-json",
                        "plugin-json",
                        1001L
                ),
                "trace-2"
        );

        ArgumentCaptor<AgentGenerationTask> captor = ArgumentCaptor.forClass(AgentGenerationTask.class);
        verify(agentRepository).insertGenerationTask(captor.capture());
        AgentGenerationTask inserted = captor.getValue();

        verify(orchestrationDispatcher).dispatchInitialRun(projectId, 940001L, "trace-2");
        assertThat(inserted.getTaskId()).as("taskId should be generated before insert").isEqualTo(940001L);
        assertThat(inserted.getModelConfigId()).as("generation task should persist explicit modelConfigId").isEqualTo(4001L);
        assertThat(result.getTaskId()).isEqualTo(inserted.getTaskId());
    }
}
