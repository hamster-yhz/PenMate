package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
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
    @Mock
    private AgentCheckpointBoundaryService checkpointBoundary;

    @Test
    void emits_llm_turn_events_and_bounded_message_delta_for_completed_text_response() {
        when(toolDefinitionSource.listLlmSchemas()).thenReturn(List.of());
        when(llmGateway.generateTurn(any(), any()))
                .thenReturn(new AgentLlmTurnResponse("stop", "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabc", List.of(), "{}", new LlmTokenUsage(7, 9, 16)));
        AgentRunLlmLoop loop = new AgentRunLlmLoop(llmGateway, toolDefinitionSource,
                eventPublisher, toolCallService, checkpointBoundary);

        AgentRunLoopResult result = loop.execute(new AgentRunLoopRequest(
                70001L,
                101L,
                90001L,
                50001L,
                "trace-1",
                List.of(AgentLlmMessage.user("Write")),
                AgentLlmExecutionConfig.builder().modelConfigId(1001L).build(),
                201L,
                7L
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
        AgentRunLlmLoop loop = new AgentRunLlmLoop(llmGateway, toolDefinitionSource,
                eventPublisher, toolCallService, checkpointBoundary);

        AgentRunLoopResult result = loop.execute(new AgentRunLoopRequest(
                70001L,
                101L,
                90001L,
                50001L,
                "trace-1",
                List.of(AgentLlmMessage.user("Create a book")),
                AgentLlmExecutionConfig.builder().modelConfigId(1001L).build(),
                201L,
                7L
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
        ArgumentCaptor<com.penmate.backend.application.agent.tool.runtime.ToolCallRequest> toolRequest =
                ArgumentCaptor.forClass(com.penmate.backend.application.agent.tool.runtime.ToolCallRequest.class);
        verify(toolCallService).executeToolCall(toolRequest.capture());
        assertThat(toolRequest.getValue().executionToken()).isEqualTo(7L);
    }

    @Test
    void resume_should_execute_remaining_sibling_tool_calls_before_requesting_the_llm_again() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        List<AgentLlmToolCallPayload> calls = List.of(
                new AgentLlmToolCallPayload("call-1", "function", "story_bible_update", "{\"operation\":\"batch\"}"),
                new AgentLlmToolCallPayload("call-2", "function", "story_bible_search", "{\"query\":\"Mira\"}")
        );
        List<AgentLlmMessage> savedMessages = List.of(
                AgentLlmMessage.user("Update and inspect Mira"),
                AgentLlmMessage.assistant("", calls)
        );
        AgentRunPendingApproval pending = new AgentRunPendingApproval(
                1L, 88001L, 88001L, 70001L, 101L, 90001L, 50001L,
                "call-1", "story_bible_update", "{\"operation\":\"batch\"}",
                "{\"llmTurnIndex\":1,\"tokenUsage\":{\"promptTokens\":3,\"completionTokens\":2,\"totalTokens\":5},\"assistantText\":\"\"}",
                objectMapper.writeValueAsString(savedMessages), "70001:call-1", "APPROVED",
                201L, "trace-1", null, null
        );
        when(toolDefinitionSource.listLlmSchemas()).thenReturn(List.of());
        when(toolCallService.executeToolCall(any())).thenReturn(
                ToolCallResult.success("{\"updated\":true}"),
                ToolCallResult.success("{\"matches\":[\"Mira\"]}")
        );
        when(llmGateway.generateTurn(any(), any())).thenReturn(new AgentLlmTurnResponse(
                "stop", "Done", List.of(), "{}", new LlmTokenUsage(4, 1, 5)));
        AgentRunLlmLoop loop = new AgentRunLlmLoop(
                llmGateway, toolDefinitionSource, eventPublisher, toolCallService, checkpointBoundary);

        AgentRunLoopResult result = loop.resumeApproved(new AgentRunLoopRequest(
                70001L, 101L, 90001L, 50001L, "trace-1", List.of(),
                AgentLlmExecutionConfig.builder().modelConfigId(1001L).build(), 201L, 7L
        ), pending);

        assertThat(result.status()).isEqualTo(AgentRunLoopResult.Status.COMPLETED);
        assertThat(result.finalAssistantText()).isEqualTo("Done");
        ArgumentCaptor<com.penmate.backend.application.agent.tool.runtime.ToolCallRequest> toolRequests =
                ArgumentCaptor.forClass(com.penmate.backend.application.agent.tool.runtime.ToolCallRequest.class);
        verify(toolCallService, org.mockito.Mockito.times(2)).executeToolCall(toolRequests.capture());
        assertThat(toolRequests.getAllValues()).extracting(
                com.penmate.backend.application.agent.tool.runtime.ToolCallRequest::toolCallId)
                .containsExactly("call-1", "call-2");
        assertThat(toolRequests.getAllValues()).extracting(
                com.penmate.backend.application.agent.tool.runtime.ToolCallRequest::executionToken)
                .containsOnly(7L);

        ArgumentCaptor<AgentLlmTurnRequest> llmRequest = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(llmGateway).generateTurn(llmRequest.capture(), any());
        assertThat(llmRequest.getValue().messages()).hasSize(4);
        assertThat(llmRequest.getValue().messages().get(2).toolCallId()).isEqualTo("call-1");
        assertThat(llmRequest.getValue().messages().get(3).toolCallId()).isEqualTo("call-2");
    }

    @Test
    void resume_should_pause_again_when_a_remaining_sibling_requires_approval() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        List<AgentLlmToolCallPayload> calls = List.of(
                new AgentLlmToolCallPayload("call-1", "function", "story_bible_update", "{\"operation\":\"batch\"}"),
                new AgentLlmToolCallPayload("call-2", "function", "book_crud", "{\"operation\":\"delete\"}")
        );
        List<AgentLlmMessage> savedMessages = List.of(
                AgentLlmMessage.user("Update the bible and delete the book"),
                AgentLlmMessage.assistant("", calls)
        );
        AgentRunPendingApproval pending = new AgentRunPendingApproval(
                1L, 88001L, 88001L, 70001L, 101L, 90001L, 50001L,
                "call-1", "story_bible_update", "{\"operation\":\"batch\"}",
                "{\"llmTurnIndex\":1,\"tokenUsage\":{\"promptTokens\":3,\"completionTokens\":2,\"totalTokens\":5},\"assistantText\":\"\"}",
                objectMapper.writeValueAsString(savedMessages), "70001:call-1", "APPROVED",
                201L, "trace-1", null, null
        );
        when(toolCallService.executeToolCall(any())).thenReturn(
                ToolCallResult.success("{\"updated\":true}"),
                ToolCallResult.waitingApproval(202L)
        );
        AgentRunLlmLoop loop = new AgentRunLlmLoop(
                llmGateway, toolDefinitionSource, eventPublisher, toolCallService, checkpointBoundary);

        AgentRunLoopResult result = loop.resumeApproved(new AgentRunLoopRequest(
                70001L, 101L, 90001L, 50001L, "trace-1", List.of(),
                AgentLlmExecutionConfig.builder().modelConfigId(1001L).build(), 201L, 7L
        ), pending);

        assertThat(result.status()).isEqualTo(AgentRunLoopResult.Status.WAITING_APPROVAL);
        assertThat(result.approvalId()).isEqualTo(202L);
        verify(llmGateway, never()).generateTurn(any(), any());
        ArgumentCaptor<com.penmate.backend.application.agent.tool.runtime.ToolCallRequest> requests =
                ArgumentCaptor.forClass(com.penmate.backend.application.agent.tool.runtime.ToolCallRequest.class);
        verify(toolCallService, org.mockito.Mockito.times(2)).executeToolCall(requests.capture());
        var siblingRequest = requests.getAllValues().get(1);
        assertThat(siblingRequest.toolCallId()).isEqualTo("call-2");
        assertThat(siblingRequest.executionToken()).isEqualTo(7L);
        assertThat(siblingRequest.conversationMessagesJson()).contains("call-1").contains("updated");
    }
}
