package com.penmate.backend.infrastructure.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.run.AgentEventPayloadResolver;
import com.penmate.backend.application.agent.run.AgentPartialMessageCheckpointStore;
import com.penmate.backend.interfaces.api.agent.stream.AgentRunEventStreamService;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentEventWindow;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import com.penmate.backend.interfaces.api.agent.dto.AgentRunEventDto;
import com.penmate.backend.interfaces.api.agent.dto.AgentStreamResetDto;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentRunEventStreamServiceTest {

    @Test
    void emits_reset_and_advances_to_latest_when_cursor_is_expired() {
        AgentRunEventRepository events = mock(AgentRunEventRepository.class);
        InMemoryAgentRunEventBus bus = mock(InMemoryAgentRunEventBus.class);
        when(bus.subscribe(any(), any())).thenReturn(() -> { });
        when(events.findWindow(70L)).thenReturn(new AgentEventWindow(51L, 80L));
        when(events.listAfter(70L, 80L)).thenReturn(List.of());
        CapturingEmitter emitter = new CapturingEmitter();

        service(events, bus, emitter).openStream(70L, 49L);

        assertThat(emitter.payloads()).filteredOn(AgentStreamResetDto.class::isInstance)
                .singleElement()
                .isEqualTo(new AgentStreamResetDto("70", "49", "51", "80", "CURSOR_EXPIRED"));
        verify(events).listAfter(70L, 80L);
        verify(events, never()).listAfter(70L, 49L);
    }

    @Test
    void database_poll_delivers_durable_events_without_bus_notification() {
        AgentRunEventRepository events = mock(AgentRunEventRepository.class);
        InMemoryAgentRunEventBus bus = mock(InMemoryAgentRunEventBus.class);
        when(bus.subscribe(any(), any())).thenReturn(() -> { });
        when(events.findWindow(70L))
                .thenReturn(new AgentEventWindow(null, 0L))
                .thenReturn(new AgentEventWindow(1L, 1L));
        AgentEvent event = event(1L, "run.phase.changed");
        when(events.listAfter(70L, 0L)).thenReturn(List.of(), List.of(event));
        CapturingEmitter emitter = new CapturingEmitter();
        AgentRunEventStreamService service = service(events, bus, emitter);

        service.openStream(70L, 0L);
        service.pollActiveStreams();

        assertThat(emitter.payloads()).filteredOn(AgentRunEventDto.class::isInstance)
                .map(AgentRunEventDto.class::cast)
                .extracting(AgentRunEventDto::sequence)
                .containsExactly("1", "1");
        assertThat(emitter.payloads()).filteredOn(AgentRunEventDto.class::isInstance)
                .map(AgentRunEventDto.class::cast)
                .extracting(AgentRunEventDto::createdAt)
                .containsExactly("2026-07-17T00:00:00Z", "2026-07-17T00:00:00Z");
    }

    @Test
    void bus_notification_wakes_database_replay_in_sequence_order() {
        AgentRunEventRepository events = mock(AgentRunEventRepository.class);
        InMemoryAgentRunEventBus bus = mock(InMemoryAgentRunEventBus.class);
        ArgumentCaptor<Consumer<AgentEvent>> subscriber = ArgumentCaptor.forClass(Consumer.class);
        when(bus.subscribe(eq(70L), subscriber.capture())).thenReturn(() -> { });
        when(events.findWindow(70L))
                .thenReturn(new AgentEventWindow(null, 0L))
                .thenReturn(new AgentEventWindow(1L, 2L));
        when(events.listAfter(70L, 0L)).thenReturn(
                List.of(), List.of(event(1L, "run.started"), event(2L, "run.phase.changed")));
        CapturingEmitter emitter = new CapturingEmitter();

        service(events, bus, emitter).openStream(70L, 0L);
        subscriber.getValue().accept(event(2L, "run.phase.changed"));

        assertThat(emitter.payloads()).filteredOn(AgentRunEventDto.class::isInstance)
                .map(AgentRunEventDto.class::cast)
                .extracting(AgentRunEventDto::sequence)
                .containsExactly("1", "1", "2", "2");
    }

    @Test
    void sends_the_latest_partial_message_snapshot_when_a_stream_reconnects() {
        AgentRunEventRepository events = mock(AgentRunEventRepository.class);
        InMemoryAgentRunEventBus bus = mock(InMemoryAgentRunEventBus.class);
        AgentPartialMessageCheckpointStore partialMessages = mock(AgentPartialMessageCheckpointStore.class);
        when(bus.subscribe(any(), any())).thenReturn(() -> { });
        when(events.findWindow(70L)).thenReturn(new AgentEventWindow(null, 0L));
        when(events.listAfter(70L, 0L)).thenReturn(List.of());
        when(partialMessages.find(70L)).thenReturn(Optional.of(
                new AgentPartialMessageCheckpointStore.Snapshot(
                        70L, 30L, "partial answer", 14L, Instant.parse("2026-07-21T08:00:00Z"))));
        CapturingEmitter emitter = new CapturingEmitter();
        AgentRunEventStreamService service = new AgentRunEventStreamService(
                events, bus, new AgentEventPayloadResolver(
                mock(AgentArtifactRepository.class), new JacksonJsonCodec(new ObjectMapper())),
                partialMessages, new ObjectMapper()) {
            @Override
            protected SseEmitter createEmitter() {
                return emitter;
            }
        };

        service.openStream(70L, 0L);

        assertThat(emitter.payloads()).filteredOn(AgentRunEventDto.class::isInstance)
                .map(AgentRunEventDto.class::cast)
                .filteredOn(dto -> "message.snapshot".equals(dto.type()))
                .hasSize(2)
                .allSatisfy(dto -> assertThat(dto.payloadJson()).contains("partial answer"));
    }

    private AgentRunEventStreamService service(AgentRunEventRepository events,
                                               InMemoryAgentRunEventBus bus,
                                               CapturingEmitter emitter) {
        return new AgentRunEventStreamService(
                events, bus, new AgentEventPayloadResolver(
                mock(AgentArtifactRepository.class), new JacksonJsonCodec(new ObjectMapper())),
                mock(AgentPartialMessageCheckpointStore.class), new ObjectMapper()) {
            @Override
            protected SseEmitter createEmitter() {
                return emitter;
            }
        };
    }

    private AgentEvent event(long sequence, String type) {
        return new AgentEvent(100L + sequence, 70L, 10L, 20L, 30L, sequence,
                1, type, "{}", java.time.LocalDateTime.of(2026, 7, 17, 0, 0).toInstant(java.time.ZoneOffset.UTC));
    }

    private static final class CapturingEmitter extends SseEmitter {
        private final List<Object> payloads = new ArrayList<>();

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            builder.build().forEach(item -> payloads.add(item.getData()));
        }

        private List<Object> payloads() {
            return payloads;
        }
    }
}
