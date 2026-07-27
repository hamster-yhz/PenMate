package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmCancellationPort;
import com.penmate.backend.application.agent.llm.AgentLlmInvocationService;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmMessageRole;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.model.AgentRunContinuation;
import com.penmate.backend.domain.agent.run.model.AgentRunNoProgressState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
    @Mock
    private AgentRunContinuationArtifactService continuations;
    @Mock
    private AgentLlmCancellationPort cancellations;
    @Mock
    private AgentPartialMessageCheckpointStore partialMessages;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(continuations.save(any())).thenReturn(
                new AgentRunContinuationArtifactService.ArtifactRef(99001L, "key", "a".repeat(64), 100));
        org.mockito.Mockito.lenient().when(cancellations.register(anyLong(), any())).thenReturn(() -> { });
    }

    @Test
    void emits_llm_turn_events_and_bounded_message_delta_for_completed_text_response() {
        when(llmGateway.generateTurn(any(), any()))
                .thenReturn(new AgentLlmTurnResponse("stop", "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabc", List.of(), "{}", new LlmTokenUsage(7, 9, 16)));
        AgentRunLlmLoop loop = newLoop();

        AgentRunLoopResult result = loop.execute(new AgentRunLoopRequest(
                70001L,
                101L,
                90001L,
                50001L,
                "trace-1",
                List.of(AgentLlmMessage.user("Write")),
                List.of(new AgentLlmToolSchema(
                        "snapshot_tool", "Snapshot tool", "{\"type\":\"object\"}")),
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
        ArgumentCaptor<AgentLlmTurnRequest> llmRequest = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(llmGateway).generateTurn(llmRequest.capture(), any());
        assertThat(llmRequest.getValue().tools())
                .extracting(AgentLlmToolSchema::toolCode)
                .containsExactly("snapshot_tool");
    }

    @Test
    void sends_failed_tool_call_back_to_llm_before_completing_run() {
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
        AgentRunLlmLoop loop = newLoop();

        AgentRunLoopResult result = loop.execute(new AgentRunLoopRequest(
                70001L,
                101L,
                90001L,
                50001L,
                "trace-1",
                List.of(AgentLlmMessage.user("Create a book")),
                List.of(new AgentLlmToolSchema(
                        "book_crud", "Book CRUD", "{\"type\":\"object\"}")),
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
    void rejects_a_fresh_tool_call_that_is_not_in_the_run_snapshot() {
        when(llmGateway.generateTurn(any(), any()))
                .thenReturn(
                        new AgentLlmTurnResponse(
                                "tool_calls", "",
                                List.of(new AgentLlmToolCall("call-hidden", "book_crud", "{}")),
                                "{}", new LlmTokenUsage(1, 1, 2)),
                        new AgentLlmTurnResponse(
                                "stop", "Not available", List.of(), "{}",
                                new LlmTokenUsage(1, 1, 2))
                );
        AgentRunLlmLoop loop = newLoop();

        AgentRunLoopResult result = loop.execute(new AgentRunLoopRequest(
                70001L, 101L, 90001L, 50001L, "trace-allowlist",
                List.of(AgentLlmMessage.user("Try a hidden tool")),
                List.of(new AgentLlmToolSchema(
                        "rag_query", "RAG", "{\"type\":\"object\"}")),
                AgentLlmExecutionConfig.builder().build(), 201L, 7L));

        assertThat(result.status()).isEqualTo(AgentRunLoopResult.Status.COMPLETED);
        verify(toolCallService, never()).executeToolCall(any());
        ArgumentCaptor<Object> failedPayload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(eq(70001L), eq("tool.call.failed"), failedPayload.capture());
        assertThat(((Map<?, ?>) failedPayload.getValue()).get("errorCode"))
                .isEqualTo("TOOL_NOT_ALLOWED_FOR_RUN");
    }

    @Test
    void resume_should_ignore_stale_pending_identity_and_execute_remaining_sibling_calls() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        List<AgentLlmToolCallPayload> calls = List.of(
                new AgentLlmToolCallPayload("call-1", "function", "story_bible_node_write", "{\"operation\":\"update\"}"),
                new AgentLlmToolCallPayload("call-2", "function", "story_bible_search", "{\"query\":\"Mira\"}")
        );
        List<AgentLlmMessage> savedMessages = List.of(
                AgentLlmMessage.user("Update and inspect Mira"),
                AgentLlmMessage.assistant("", calls)
        );
        AgentRunPendingApproval pending = new AgentRunPendingApproval(
                1L, 88001L, 88001L, 70001L, 999L, 998L, 997L,
                "call-1", "story_bible_node_write", "{\"operation\":\"update\"}",
                "{\"llmTurnIndex\":1,\"tokenUsage\":{\"promptTokens\":3,\"completionTokens\":2,\"totalTokens\":5},\"assistantText\":\"\"}",
                objectMapper.writeValueAsString(savedMessages), "70001:call-1", "APPROVED",
                996L, "stale-snapshot-trace", null, null
        );
        when(toolCallService.executeToolCall(any())).thenReturn(
                ToolCallResult.success("{\"updated\":true}"),
                ToolCallResult.success("{\"matches\":[\"Mira\"]}")
        );
        when(llmGateway.generateTurn(any(), any())).thenReturn(new AgentLlmTurnResponse(
                "stop", "Done", List.of(), "{}", new LlmTokenUsage(4, 1, 5)));
        AgentRunLlmLoop loop = newLoop();

        AgentRunLoopResult result = loop.resumeApproved(new AgentRunLoopRequest(
                70001L, 101L, 90001L, 50001L, "trace-1", List.of(),
                List.of(),
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
                new AgentLlmToolCallPayload("call-1", "function", "story_bible_node_write", "{\"operation\":\"update\"}"),
                new AgentLlmToolCallPayload("call-2", "function", "book_crud", "{\"operation\":\"delete\"}")
        );
        List<AgentLlmMessage> savedMessages = List.of(
                AgentLlmMessage.user("Update the bible and delete the book"),
                AgentLlmMessage.assistant("", calls)
        );
        AgentRunPendingApproval pending = new AgentRunPendingApproval(
                1L, 88001L, 88001L, 70001L, 101L, 90001L, 50001L,
                "call-1", "story_bible_node_write", "{\"operation\":\"update\"}",
                "{\"llmTurnIndex\":1,\"tokenUsage\":{\"promptTokens\":3,\"completionTokens\":2,\"totalTokens\":5},\"assistantText\":\"\"}",
                objectMapper.writeValueAsString(savedMessages), "70001:call-1", "APPROVED",
                201L, "trace-1", null, null
        );
        when(toolCallService.executeToolCall(any())).thenReturn(
                ToolCallResult.success("{\"updated\":true}"),
                ToolCallResult.waitingApproval(202L, Map.of("nodeId", "71"))
        );
        AgentRunLlmLoop loop = newLoop();

        AgentRunLoopResult result = loop.resumeApproved(new AgentRunLoopRequest(
                70001L, 101L, 90001L, 50001L, "trace-1", List.of(),
                List.of(),
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
        ArgumentCaptor<Object> waitingPayload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(eq(70001L), eq("tool.call.waiting_approval"), waitingPayload.capture());
        assertThat(((Map<?, ?>) waitingPayload.getValue()).get("approvalPreview"))
                .isEqualTo(Map.of("nodeId", "71"));
    }

    @Test
    void resumes_from_ready_for_tool_with_the_same_tool_call_before_next_llm_turn() {
        List<AgentLlmToolCallPayload> calls = List.of(
                new AgentLlmToolCallPayload("call-recover", "function", "story_bible_search", "{\"query\":\"Mira\"}"));
        List<AgentLlmMessage> messages = List.of(
                AgentLlmMessage.user("Find Mira"),
                AgentLlmMessage.assistant("", calls));
        AgentRunContinuation continuation = AgentRunContinuation.readyForTool(
                70001L, messages, 2, 1, 0, "", new LlmTokenUsage(4, 2, 6));
        when(toolCallService.executeToolCall(any())).thenReturn(
                ToolCallResult.success("{\"matches\":[\"Mira\"]}"));
        when(llmGateway.generateTurn(any(), any())).thenReturn(new AgentLlmTurnResponse(
                "stop", "Recovered", List.of(), "{}", new LlmTokenUsage(2, 1, 3)));
        AgentRunLlmLoop loop = newLoop();

        AgentRunLoopResult result = loop.resume(new AgentRunLoopRequest(
                70001L, 101L, 90001L, 50001L, "trace", List.of(),
                List.of(),
                AgentLlmExecutionConfig.builder().build(), 201L, 7L), continuation);

        assertThat(result.finalAssistantText()).isEqualTo("Recovered");
        ArgumentCaptor<com.penmate.backend.application.agent.tool.runtime.ToolCallRequest> toolRequest =
                ArgumentCaptor.forClass(com.penmate.backend.application.agent.tool.runtime.ToolCallRequest.class);
        verify(toolCallService).executeToolCall(toolRequest.capture());
        assertThat(toolRequest.getValue().toolCallId()).isEqualTo("call-recover");
        assertThat(toolRequest.getValue().executionToken()).isEqualTo(7L);
        ArgumentCaptor<AgentLlmTurnRequest> llmRequest = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(llmGateway).generateTurn(llmRequest.capture(), any());
        assertThat(llmRequest.getValue().messages()).extracting(AgentLlmMessage::role)
                .containsExactly(AgentLlmMessageRole.USER, AgentLlmMessageRole.ASSISTANT,
                        AgentLlmMessageRole.TOOL);
    }

    @Test
    void completed_continuation_finishes_without_calling_llm_or_tools() {
        AgentRunContinuation continuation = AgentRunContinuation.completed(
                70001L, List.of(), 2, 1, "Already complete", new LlmTokenUsage(4, 2, 6));
        AgentRunLlmLoop loop = newLoop();

        AgentRunLoopResult result = loop.resume(new AgentRunLoopRequest(
                70001L, 101L, 90001L, 50001L, "trace", List.of(),
                List.of(),
                AgentLlmExecutionConfig.builder().build(), 201L, 7L), continuation);

        assertThat(result.finalAssistantText()).isEqualTo("Already complete");
        verify(llmGateway, never()).generateTurn(any(), any());
        verify(toolCallService, never()).executeToolCall(any());
    }

    @Test
    void continues_past_ten_tool_turns_when_each_call_makes_progress() {
        AtomicInteger llmTurn = new AtomicInteger();
        when(llmGateway.generateTurn(any(), any())).thenAnswer(invocation -> {
            int turn = llmTurn.incrementAndGet();
            if (turn == 12) {
                return new AgentLlmTurnResponse("stop", "Done after eleven calls", List.of(), "{}",
                        new LlmTokenUsage(1, 1, 2));
            }
            return new AgentLlmTurnResponse("tool_calls", "", List.of(
                    new AgentLlmToolCall("call-" + turn, "manuscript_chapter_read",
                            "{\"chapterId\":" + turn + "}")), "{}", new LlmTokenUsage(1, 1, 2));
        });
        AtomicInteger toolTurn = new AtomicInteger();
        when(toolCallService.executeToolCall(any())).thenAnswer(invocation ->
                ToolCallResult.success("{\"chapterId\":" + toolTurn.incrementAndGet()
                        + ",\"revision\":1}"));

        AgentRunLoopResult result = newLoop().execute(requestWithTool("manuscript_chapter_read"));

        assertThat(result.status()).isEqualTo(AgentRunLoopResult.Status.COMPLETED);
        assertThat(result.finalAssistantText()).isEqualTo("Done after eleven calls");
        verify(llmGateway, org.mockito.Mockito.times(12)).generateTurn(any(), any());
        verify(toolCallService, org.mockito.Mockito.times(11)).executeToolCall(any());
    }

    @Test
    void fails_after_eight_identical_normalized_tool_calls_and_results() {
        AtomicInteger llmTurn = new AtomicInteger();
        when(llmGateway.generateTurn(any(), any())).thenAnswer(invocation -> {
            int turn = llmTurn.incrementAndGet();
            return new AgentLlmTurnResponse("tool_calls", "", List.of(
                    new AgentLlmToolCall("call-" + turn, "story_bible_search",
                            "{ \"query\" : \"Mira\" }")), "{}", new LlmTokenUsage(1, 1, 2));
        });
        when(toolCallService.executeToolCall(any()))
                .thenReturn(ToolCallResult.success("{\"matches\":[]}"));

        AgentRunLoopResult result = newLoop().execute(requestWithTool("story_bible_search"));

        assertThat(result.status()).isEqualTo(AgentRunLoopResult.Status.FAILED);
        assertThat(result.finalAssistantText()).contains("IDENTICAL_CALL_REPEATED");
        verify(llmGateway, org.mockito.Mockito.times(8)).generateTurn(any(), any());
        verify(eventPublisher).publish(eq(70001L), eq("run.no_progress_detected"), any());
    }

    @Test
    void fails_when_twenty_consecutive_distinct_writes_report_no_state_change() {
        AtomicInteger llmTurn = new AtomicInteger();
        when(llmGateway.generateTurn(any(), any())).thenAnswer(invocation -> {
            AgentLlmTurnRequest request = invocation.getArgument(0);
            if (request.tools().isEmpty()) {
                return new AgentLlmTurnResponse("stop", "{\"summary\":\"No state changes yet\"}",
                        List.of(), "{}", new LlmTokenUsage(10, 5, 15));
            }
            int turn = llmTurn.incrementAndGet();
            return new AgentLlmTurnResponse("tool_calls", "", List.of(
                    new AgentLlmToolCall("call-" + turn, "story_bible_node_write",
                            "{\"items\":[{\"operation\":\"update\",\"nonce\":" + turn + "}]}")),
                    "{}", new LlmTokenUsage(1, 1, 2));
        });
        when(toolCallService.executeToolCall(any())).thenReturn(
                ToolCallResult.success("{\"changed\":false,\"nodeId\":71,\"revision\":3}"));

        AgentRunLoopRequest request = new AgentRunLoopRequest(
                70001L, 101L, 90001L, 50001L, "trace",
                List.of(AgentLlmMessage.user("x".repeat(145_000))),
                List.of(new AgentLlmToolSchema("story_bible_node_write", "write",
                        "{\"type\":\"object\"}")),
                AgentLlmExecutionConfig.builder().maxContextTokens(50_000).maxOutputTokens(100).build(),
                201L, 7L);
        AgentRunLoopResult result = newLoop().execute(request);

        assertThat(result.status()).isEqualTo(AgentRunLoopResult.Status.FAILED);
        assertThat(result.finalAssistantText()).contains("NO_PROGRESS_IN_LAST_20_CALLS");
        verify(toolCallService, org.mockito.Mockito.times(21)).executeToolCall(any());
        verify(eventPublisher).publish(eq(70001L), eq("context.compression.completed"), any());
        verify(eventPublisher).publish(eq(70001L), eq("run.no_progress_detected"), any());
    }

    @Test
    void preserves_no_progress_window_when_resuming_a_tool_continuation() {
        AgentRunNoProgressState state = AgentRunNoProgressState.EMPTY;
        for (int index = 1; index <= 20; index++) {
            state = state.append("story_bible_node_write\nnonce=" + index,
                    java.util.Set.of("nodeId=71", "revision=3"), false);
        }
        AgentLlmToolCallPayload call = new AgentLlmToolCallPayload(
                "call-21", "function", "story_bible_node_write", "{\"nonce\":21}");
        AgentRunContinuation continuation = AgentRunContinuation.readyForTool(
                70001L, List.of(AgentLlmMessage.assistant("", List.of(call))),
                21, 20, 0, "", new LlmTokenUsage(20, 20, 40), state);
        when(toolCallService.executeToolCall(any())).thenReturn(
                ToolCallResult.success("{\"changed\":false,\"nodeId\":71,\"revision\":3}"));

        AgentRunLoopResult result = newLoop().resume(
                requestWithTool("story_bible_node_write"), continuation);

        assertThat(result.status()).isEqualTo(AgentRunLoopResult.Status.FAILED);
        assertThat(result.finalAssistantText()).contains("NO_PROGRESS_IN_LAST_20_CALLS");
        verify(toolCallService).executeToolCall(any());
        verify(llmGateway, never()).generateTurn(any(), any());
    }

    @Test
    void rejected_approval_resumes_with_user_rejected_result_and_caches_same_call() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AgentLlmToolCallPayload rejectedCall = new AgentLlmToolCallPayload(
                "call-rejected", "function", "story_bible_node_write", "{\"items\":[{\"operation\":\"update\"}]}" );
        List<AgentLlmMessage> savedMessages = List.of(
                AgentLlmMessage.user("Update canon"),
                AgentLlmMessage.assistant("", List.of(rejectedCall)));
        AgentRunPendingApproval pending = new AgentRunPendingApproval(
                1L, 88001L, 88001L, 70001L, 101L, 90001L, 50001L,
                "call-rejected", "story_bible_node_write", rejectedCall.argumentsJson(),
                "{\"llmTurnIndex\":1,\"iterationIndex\":0,\"tokenUsage\":{\"promptTokens\":1,\"completionTokens\":1,\"totalTokens\":2},\"assistantText\":\"\"}",
                objectMapper.writeValueAsString(savedMessages), "70001:call-rejected", "REJECTED",
                201L, "trace", null, null);
        when(llmGateway.generateTurn(any(), any())).thenReturn(
                new AgentLlmTurnResponse("tool_calls", "", List.of(
                        new AgentLlmToolCall("call-again", "story_bible_node_write",
                                "{\"items\":[{\"operation\":\"update\"}]}")), "{}", new LlmTokenUsage(1, 1, 2)),
                new AgentLlmTurnResponse("stop", "Understood", List.of(), "{}", new LlmTokenUsage(1, 1, 2)));

        AgentRunLoopResult result = newLoop().resumeRejected(
                requestWithTool("story_bible_node_write"), pending);

        assertThat(result.status()).isEqualTo(AgentRunLoopResult.Status.COMPLETED);
        assertThat(result.finalAssistantText()).isEqualTo("Understood");
        verify(toolCallService, never()).executeToolCall(any());
        ArgumentCaptor<AgentLlmTurnRequest> requests = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(llmGateway, org.mockito.Mockito.times(2)).generateTurn(requests.capture(), any());
        assertThat(requests.getAllValues().get(1).messages().getLast().content())
                .contains("USER_REJECTED");
    }

    @Test
    void automatically_compresses_once_at_ninety_five_percent_before_main_invocation() {
        when(llmGateway.generateTurn(any(), any())).thenReturn(
                new AgentLlmTurnResponse("stop", """
                        {"summary":"Keep the active request","decisions":[],"completed":[],
                         "resourceState":[],"unresolved":[],"nextAction":"Continue"}
                        """, List.of(), "{}", new LlmTokenUsage(40, 10, 50)),
                new AgentLlmTurnResponse("stop", "Completed", List.of(), "{}",
                        new LlmTokenUsage(30, 5, 35)));
        AgentRunLoopRequest request = new AgentRunLoopRequest(
                70001L, 101L, 90001L, 50001L, "trace",
                List.of(AgentLlmMessage.user("x".repeat(3_000))), List.of(),
                AgentLlmExecutionConfig.builder().maxContextTokens(1_000).maxOutputTokens(100).build(),
                201L, 7L);

        AgentRunLoopResult result = newLoop().execute(request);

        assertThat(result.status()).isEqualTo(AgentRunLoopResult.Status.COMPLETED);
        assertThat(result.tokenUsage().totalTokens()).isEqualTo(85);
        ArgumentCaptor<AgentLlmTurnRequest> requests = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(llmGateway, org.mockito.Mockito.times(2)).generateTurn(requests.capture(), any());
        assertThat(requests.getAllValues().get(0).tools()).isEmpty();
        assertThat(requests.getAllValues().get(0).toolChoice()).isEqualTo("none");
        assertThat(requests.getAllValues().get(1).messages())
                .anyMatch(message -> message.content().startsWith("<compacted_conversation_context>"));
        verify(eventPublisher).publish(eq(70001L), eq("context.compression.completed"), any());
    }

    @Test
    void compression_failure_fails_run_without_a_second_attempt() {
        when(llmGateway.generateTurn(any(), any())).thenReturn(
                new AgentLlmTurnResponse("stop", "not json", List.of(), "{}", LlmTokenUsage.ZERO));
        AgentRunLoopRequest request = new AgentRunLoopRequest(
                70001L, 101L, 90001L, 50001L, "trace",
                List.of(AgentLlmMessage.user("x".repeat(3_000))), List.of(),
                AgentLlmExecutionConfig.builder().maxContextTokens(1_000).maxOutputTokens(100).build(),
                201L, 7L);

        AgentRunLoopResult result = newLoop().execute(request);

        assertThat(result.status()).isEqualTo(AgentRunLoopResult.Status.FAILED);
        assertThat(result.finalAssistantText()).contains("invalid structured summary");
        verify(llmGateway).generateTurn(any(), any());
        verify(eventPublisher).publish(eq(70001L), eq("context.compression.failed"), any());
    }

    private AgentRunLoopRequest requestWithTool(String toolCode) {
        return new AgentRunLoopRequest(
                70001L, 101L, 90001L, 50001L, "trace", List.of(AgentLlmMessage.user("Work")),
                List.of(new AgentLlmToolSchema(toolCode, toolCode, "{\"type\":\"object\"}")),
                AgentLlmExecutionConfig.builder().build(), 201L, 7L);
    }

    private AgentRunLlmLoop newLoop() {
        AgentLlmInvocationService invocations = new AgentLlmInvocationService(llmGateway, cancellations);
        AgentStreamingMessageService streaming = new AgentStreamingMessageService(eventPublisher, partialMessages);
        return new AgentRunLlmLoop(invocations, toolDefinitionSource, eventPublisher, toolCallService,
                checkpointBoundary, continuations, streaming,
                new JacksonJsonCodec(new ObjectMapper().findAndRegisterModules()));
    }
}
