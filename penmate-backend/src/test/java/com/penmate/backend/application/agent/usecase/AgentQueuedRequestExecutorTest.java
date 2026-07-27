package com.penmate.backend.application.agent.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.model.AgentQueuedRequest;
import com.penmate.backend.domain.agent.repository.AgentQueuedRequestRepository;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentQueuedRequestExecutorTest {
    private final AgentQueuedRequestRepository requests = mock(AgentQueuedRequestRepository.class);
    private final AgentTurnAppService turns = mock(AgentTurnAppService.class);
    private final AgentContextCompressionService compression = mock(AgentContextCompressionService.class);
    private final JacksonJsonCodec json = new JacksonJsonCodec(new ObjectMapper());
    private final AgentQueuedRequestExecutor executor = new AgentQueuedRequestExecutor(
            requests, turns, compression, json);

    @Test
    void claim_execution_and_completion_share_one_transaction_boundary() throws Exception {
        assertThat(AgentQueuedRequestExecutor.class.getMethod("executeNext")
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void creates_a_normal_turn_for_a_queued_message_then_completes_the_request() {
        var payload = new AgentQueuedRequestApplicationService.QueueMessagePayload(
                "next message", List.of("novel-review"),
                new AgentQueuedRequestApplicationService.TaskRequest(50L, java.util.List.of(50L), 60L, "selection"));
        when(requests.claimNextIdle()).thenReturn(request("MESSAGE", json.write(payload), 1));

        executor.executeNext();

        verify(turns).createTurn(eq(10L), eq(20L), argThat(command ->
                        command.operatorId().equals(30L)
                                && command.userMessage().equals("next message")
                                && command.activeSkills().equals(List.of("novel-review"))
                                && command.taskRequest().chapterId().equals(50L)),
                eq("queued-message-40"));
        verify(requests).markCompleted(40L);
    }

    @Test
    void runs_manual_compression_without_creating_a_turn() {
        when(requests.claimNextIdle()).thenReturn(request("COMPRESS", null, 1));

        executor.executeNext();

        verify(compression).compress(10L, 20L, 30L, "queued-context-compression-40");
        verifyNoInteractions(turns);
        verify(requests).markCompleted(40L);
    }

    @Test
    void requeues_a_transient_failure_before_the_third_attempt() {
        when(requests.claimNextIdle()).thenReturn(request("COMPRESS", null, 2));
        when(compression.compress(anyLong(), anyLong(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("temporary"));

        executor.executeNext();

        verify(requests).requeue(40L, "temporary");
        verify(requests, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void marks_the_request_failed_after_the_third_attempt() {
        when(requests.claimNextIdle()).thenReturn(request("COMPRESS", null, 3));
        when(compression.compress(anyLong(), anyLong(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("permanent"));

        executor.executeNext();

        verify(requests).markFailed(40L, "permanent");
        verify(requests, never()).requeue(anyLong(), anyString());
    }

    private AgentQueuedRequest request(String type, String payload, int attempts) {
        return new AgentQueuedRequest(40L, 10L, 20L, 30L, type, payload,
                "EXECUTING", attempts, null, Instant.now(), Instant.now());
    }
}
