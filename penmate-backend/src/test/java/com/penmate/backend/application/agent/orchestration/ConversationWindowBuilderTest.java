package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationWindowBuilderTest {

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private ConversationWindowBuilder conversationWindowBuilder;

    @Test
    void should_sort_by_seq_and_return_last_two_completed_turns_without_current_prompt_duplication() {
        when(agentRepository.listMessages(9L)).thenReturn(List.of(
                message(1004L, "assistant", "第一轮回答", 4),
                message(1001L, "user", "第一轮提问", 1),
                message(1003L, "user", "第二轮提问", 3),
                message(1006L, "user", "当前提问", 6),
                message(1002L, "assistant", "第一轮补充", 2),
                message(1005L, "assistant", "第二轮回答", 5)
        ));

        List<AgentLlmMessage> result = conversationWindowBuilder.build(9L, "当前提问", 2);

        assertThat(result).extracting(AgentLlmMessage::content)
                .containsExactly("第一轮提问", "第一轮补充", "第一轮回答", "第二轮提问", "第二轮回答");
    }

    @Test
    void should_return_empty_when_window_is_disabled() {
        assertThat(conversationWindowBuilder.build(9L, "当前提问", 0)).isEmpty();
        assertThat(conversationWindowBuilder.build(9L, "当前提问", null)).isEmpty();
    }

    @Test
    void should_build_from_messages_strictly_before_the_current_turn() {
        when(agentRepository.listMessagesBeforeTurn(9L, 77L)).thenReturn(List.of(
                message(1001L, "user", "Earlier request", 1),
                message(1002L, "assistant", "Earlier answer", 2)
        ));

        List<AgentLlmMessage> result = conversationWindowBuilder.buildBeforeTurn(9L, 77L, 8);

        assertThat(result).extracting(AgentLlmMessage::content)
                .containsExactly("Earlier request", "Earlier answer");
    }

    private AgentMessage message(Long messageId, String role, String content, int seqNo) {
        AgentMessage message = new AgentMessage();
        message.setMessageId(messageId);
        message.setConversationId(9L);
        message.setRole(role);
        message.setContentMd(content);
        message.setSeqNo(seqNo);
        return message;
    }
}
