package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.command.AgentCommands.CreateMessageCommand;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentMessageAppServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

    @InjectMocks
    private AgentMessageAppService agentMessageAppService;

    @Test
    void UT_APP_AGENT_CREATE_MESSAGE_SHOULD_GENERATE_MESSAGE_ID_BEFORE_INSERT() {
        Long projectId = 920001L;
        Long conversationId = 920002L;
        AgentConversation conversation = new AgentConversation();
        conversation.setConversationId(conversationId);

        when(agentRepository.findConversation(projectId, conversationId)).thenReturn(conversation);
        when(businessIdGenerator.nextId()).thenReturn(930001L);
        when(agentRepository.nextMessageSeq(conversationId)).thenReturn(1);
        when(agentRepository.insertMessage(org.mockito.ArgumentMatchers.any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(conversationId)).thenReturn(1);

        AgentMessage result = agentMessageAppService.createMessage(
                projectId,
                conversationId,
                new CreateMessageCommand(
                        "user",
                        "text",
                        "hello",
                        null,
                        null,
                        1001L
                ),
                "trace-1"
        );

        ArgumentCaptor<AgentMessage> captor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentRepository).insertMessage(captor.capture());
        AgentMessage inserted = captor.getValue();
        InOrder inOrder = inOrder(businessIdGenerator, agentRepository);
        inOrder.verify(businessIdGenerator).nextId();
        inOrder.verify(agentRepository).insertMessage(org.mockito.ArgumentMatchers.any(AgentMessage.class));

        assertThat(inserted.getMessageId()).as("messageId should be generated before insert").isEqualTo(930001L);
        assertThat(result.getMessageId()).isEqualTo(inserted.getMessageId());
        assertThat(inserted.getConversationId()).isEqualTo(conversationId);
        assertThat(inserted.getSeqNo()).isEqualTo(1);
    }
}
