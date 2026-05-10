package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.domain.agent.model.AgentGenerationTask;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTaskResultRecorderTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

    @InjectMocks
    private AgentTaskResultRecorder agentTaskResultRecorder;

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_GENERATE_MESSAGE_ID_BEFORE_INSERT() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setConversationId(920002L);

        when(businessIdGenerator.nextId()).thenReturn(930001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(920002L)).thenReturn(1);

        agentTaskResultRecorder.recordAssistantResult(task, "生成完成");

        ArgumentCaptor<AgentMessage> captor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentRepository).insertMessage(captor.capture());
        AgentMessage inserted = captor.getValue();

        InOrder inOrder = inOrder(businessIdGenerator, agentRepository);
        inOrder.verify(businessIdGenerator).nextId();
        inOrder.verify(agentRepository).nextMessageSeq(920002L);
        inOrder.verify(agentRepository).insertMessage(any(AgentMessage.class));

        assertThat(inserted.getMessageId()).isEqualTo(930001L);
        assertThat(inserted.getConversationId()).isEqualTo(920002L);
        assertThat(inserted.getRole()).isEqualTo("assistant");
        assertThat(inserted.getUserMessageType()).isEqualTo("GENERATION_RESULT");
        assertThat(inserted.getContentMd()).isEqualTo("生成完成");
        assertThat(inserted.getSeqNo()).isEqualTo(2);
    }
}
