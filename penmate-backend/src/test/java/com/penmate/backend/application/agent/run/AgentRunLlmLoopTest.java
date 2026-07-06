package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmMessageRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
        verify(eventPublisher, never()).publish(eq(70001L), eq("message.completed"), any());
    }

    @Test
    void sends_failed_tool_call_back_to_llm_before_completing_run() {
        when(toolDefinitionSource.listLlmSchemas()).thenReturn(List.of());
        when(llmGateway.generateTurn(any(), any()))
                .thenReturn(
                        new AgentLlmTurnResponse(
                                "tool_calls",
                                "",
                                List.of(new AgentLlmToolCall("call-1", "book_crud", "{\"operation\":\"create\"}")),
                                "{}",
                                new LlmTokenUsage(3, 4, 7)
                        ),
                        new AgentLlmTurnResponse(
                                "stop",
                                "我没能完成工具调用。",
                                List.of(),
                                "{}",
                                new LlmTokenUsage(5, 6, 11)
                        )
                );
        when(toolCallService.executeToolCall(any()))
                .thenReturn(new ToolCallResult("FAILED", null, null, "BOOK_CRUD_EXECUTION_FAILED", null));
        AgentRunLlmLoop loop = new AgentRunLlmLoop(llmGateway, toolDefinitionSource, eventPublisher, toolCallService);

        AgentRunLoopResult result = loop.execute(new AgentRunLoopRequest(
                70001L,
                101L,
                90001L,
                50001L,
                "trace-1",
                List.of(AgentLlmMessage.user("Create a book")),
                AgentLlmExecutionConfig.builder().modelConfigId(1001L).build()
        ));

        assertThat(result.status()).isEqualTo(AgentRunLoopResult.Status.COMPLETED);
        assertThat(result.finalAssistantText()).isEqualTo("我没能完成工具调用。");

        ArgumentCaptor<Object> failedPayloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(eq(70001L), eq("tool.call.failed"), failedPayloadCaptor.capture());
        Map<?, ?> failedPayload = (Map<?, ?>) failedPayloadCaptor.getValue();
        assertThat(failedPayload.get("toolCallId")).isEqualTo("call-1");
        assertThat(failedPayload.get("toolCode")).isEqualTo("book_crud");
        assertThat(failedPayload.get("errorCode")).isEqualTo("BOOK_CRUD_EXECUTION_FAILED");
        assertThat(failedPayload.get("errorMessage")).isEqualTo("Unknown error");

        ArgumentCaptor<AgentLlmTurnRequest> requestCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(llmGateway, org.mockito.Mockito.times(2)).generateTurn(requestCaptor.capture(), any());
        List<AgentLlmMessage> secondTurnMessages = requestCaptor.getAllValues().get(1).messages();
        assertThat(secondTurnMessages).hasSize(3);
        assertThat(secondTurnMessages.get(1).role()).isEqualTo(AgentLlmMessageRole.ASSISTANT);
        assertThat(secondTurnMessages.get(1).toolCalls()).hasSize(1);
        assertThat(secondTurnMessages.get(2).role()).isEqualTo(AgentLlmMessageRole.TOOL);
        assertThat(secondTurnMessages.get(2).toolCallId()).isEqualTo("call-1");
        assertThat(secondTurnMessages.get(2).content()).isEqualTo("Error: Unknown error");
    }
}
