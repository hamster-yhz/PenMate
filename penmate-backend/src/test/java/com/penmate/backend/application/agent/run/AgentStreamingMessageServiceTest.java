package com.penmate.backend.application.agent.run;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentStreamingMessageServiceTest {

    @Test
    void publishes_offset_deltas_and_saves_a_cumulative_checkpoint() {
        AgentRunEventPublisher events = mock(AgentRunEventPublisher.class);
        AgentPartialMessageCheckpointStore checkpoints = mock(AgentPartialMessageCheckpointStore.class);
        AgentStreamingMessageService.StreamSession stream = new AgentStreamingMessageService(events, checkpoints)
                .open(7L, 8L, 1, "");

        stream.accept("Hel");
        stream.accept("lo");
        stream.complete("Hello");

        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeastOnce()).broadcastOnly(eq(7L), eq("message.delta"), payloads.capture(), eq(-1L));
        assertThat(payloads.getAllValues().stream()
                .map(value -> String.valueOf(((Map<?, ?>) value).get("text"))).toList())
                .containsExactly("Hel", "lo");
        assertThat(payloads.getAllValues().stream()
                .map(value -> ((Number) ((Map<?, ?>) value).get("offset")).intValue()).toList())
                .containsExactly(0, 3);

        ArgumentCaptor<AgentPartialMessageCheckpointStore.Snapshot> snapshots =
                ArgumentCaptor.forClass(AgentPartialMessageCheckpointStore.Snapshot.class);
        verify(checkpoints, atLeastOnce()).save(snapshots.capture());
        assertThat(snapshots.getAllValues().getLast().text()).isEqualTo("Hello");
        assertThat(snapshots.getAllValues().getLast().offset()).isEqualTo(5L);
    }
}
