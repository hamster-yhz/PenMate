package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunProjectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunEventPublisherTest {

    @Mock
    private AgentRunEventRepository eventRepository;
    @Mock
    private AgentProjectionUpdater projectionUpdater;
    @Mock
    private AgentRunEventBus eventBus;
    @Mock
    private AgentRunProjectionRepository runProjectionRepository;

    @Test
    void publish_appends_event_updates_projection_and_broadcasts_after_commit() {
        AgentEvent appended = new AgentEvent(1L, 70001L, 101L, 90001L, 50001L, 1L, 1,
                "run.started", "{\"schemaVersion\":1,\"phase\":\"created\"}", null);
        when(eventRepository.append(70001L, "run.started", "{\"schemaVersion\":1,\"phase\":\"created\"}"))
                .thenReturn(appended);
        AgentRunEventPublisher publisher = new AgentRunEventPublisher(
                eventRepository,
                projectionUpdater,
                eventBus,
                new ObjectMapper()
        );

        AgentEvent result = publisher.publish(70001L, "run.started", Map.of("phase", "created"));

        assertThat(result.sequence()).isEqualTo(1L);
        verify(projectionUpdater).apply(appended);
        verify(eventBus).publish(appended);
    }

    @Test
    void projection_ignores_events_at_or_below_latest_applied_sequence() {
        when(runProjectionRepository.findLatestSequence(70001L)).thenReturn(5L);
        AgentProjectionUpdater updater = new AgentProjectionUpdater(runProjectionRepository, new ObjectMapper());

        updater.apply(event(70001L, 5L, "message.delta", Map.of("text", "abc")));

        verify(runProjectionRepository, never()).appendAssistantDelta(any(), any(), any());
    }

    @Test
    void publish_adds_schema_version_to_payload_before_append() {
        AgentEvent appended = new AgentEvent(1L, 70001L, 101L, 90001L, 50001L, 1L, 1,
                "run.started", "{\"schemaVersion\":1,\"phase\":\"created\"}", null);
        when(eventRepository.append(any(), any(), any())).thenReturn(appended);
        AgentRunEventPublisher publisher = new AgentRunEventPublisher(
                eventRepository,
                projectionUpdater,
                eventBus,
                new ObjectMapper()
        );

        publisher.publish(70001L, "run.started", Map.of("phase", "created"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventRepository).append(org.mockito.Mockito.eq(70001L), org.mockito.Mockito.eq("run.started"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("\"schemaVersion\":1");
    }

    private AgentEvent event(Long runId, Long sequence, String eventType, Map<String, Object> payload) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return new AgentEvent(sequence, runId, 101L, 90001L, 50001L, sequence, 1, eventType,
                    objectMapper.writeValueAsString(payload), null);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
