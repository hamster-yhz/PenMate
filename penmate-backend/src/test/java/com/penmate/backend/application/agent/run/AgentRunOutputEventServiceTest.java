package com.penmate.backend.application.agent.run;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunOutputEventServiceTest {

    private final AgentPartialMessageCheckpointStore checkpoints = mock(AgentPartialMessageCheckpointStore.class);
    private final AgentRunEventPublisher events = mock(AgentRunEventPublisher.class);
    private final AgentRunOutputEventService service = new AgentRunOutputEventService(checkpoints, events);

    @Test
    void persists_the_exact_run_partial_as_an_interrupted_output_event() {
        Instant updatedAt = Instant.parse("2026-07-21T10:00:00Z");
        when(checkpoints.find(40L)).thenReturn(Optional.of(
                new AgentPartialMessageCheckpointStore.Snapshot(40L, 30L, "partial answer", 14L, updatedAt)));
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);

        service.persistInterrupted(40L);

        verify(events).publish(org.mockito.ArgumentMatchers.eq(40L),
                org.mockito.ArgumentMatchers.eq("message.interrupted"), payload.capture());
        assertThat(payload.getValue()).containsEntry("text", "partial answer")
                .containsEntry("offset", 14L)
                .containsEntry("updatedAt", updatedAt.toString());
    }

    @Test
    void does_not_publish_an_output_event_when_the_run_has_no_text() {
        when(checkpoints.find(40L)).thenReturn(Optional.empty());

        assertThat(service.persistInterrupted(40L)).isNull();

        verify(events, never()).publish(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
