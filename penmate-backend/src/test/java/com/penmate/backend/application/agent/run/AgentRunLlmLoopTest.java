package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunLlmLoopTest {

    @Mock
    private AgentLlmGateway llmGateway;
    @Mock
    private AgentToolDefinitionSource toolDefinitionSource;
    @Mock
    private AgentRunEventPublisher eventPublisher;
    @Mock
    private ToolCallApplicationService toolCallService;

    @Test
    void emits_llm_turn_events_and_bounded_message_delta_for_completed_text_response() {
        when(toolDefinitionSource.listLlmSchemas()).thenReturn(List.of());
        when(llmGateway.generateTurn(any(), any()))
                .thenReturn(new AgentLlmTurnResponse("stop", "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabc", List.of(), "{}", new LlmTokenUsage(7, 9, 16)));
        AgentRunLlmLoop loop = new AgentRunLlmLoop(llmGateway, toolDefinitionSource, eventPublisher, toolCallService);

        AgentRunLoopResult result = loop.execute(new AgentRunLoopRequest(
                70001L,
                101L,
                90001L,
                50001L,
                "trace-1",
                List.of(AgentLlmMessage.user("Write")),
                AgentLlmExecutionConfig.builder().modelConfigId(1001L).build()
        ));

        assertThat(result.status()).isEqualTo(AgentRunLoopResult.Status.COMPLETED);
        assertThat(result.finalAssistantText()).startsWith("abcdefghijklmnopqrstuvwxyz");
        verify(eventPublisher).publish(eq(70001L), eq("llm.turn.started"), any());
        verify(eventPublisher).publish(eq(70001L), eq("llm.turn.completed"), any());
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).broadcastOnly(eq(70001L), eq("message.delta"), payloadCaptor.capture(), anyLong());
        assertThat(payloadCaptor.getValue().toString()).contains("abcdefghijklmnopqrstuvwxyz");
        verify(eventPublisher).publish(eq(70001L), eq("message.completed"), any());
    }
}