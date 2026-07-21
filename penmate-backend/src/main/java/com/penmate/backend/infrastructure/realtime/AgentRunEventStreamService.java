package com.penmate.backend.infrastructure.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.run.AgentEventPayloadResolver;
import com.penmate.backend.application.agent.run.AgentPartialMessageCheckpointStore;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentEventWindow;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import com.penmate.backend.interfaces.api.agent.dto.AgentRunEventDto;
import com.penmate.backend.interfaces.api.agent.dto.AgentStreamResetDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class AgentRunEventStreamService {

    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final long SSE_HEARTBEAT_INTERVAL_MS = 15_000L;

    private final AgentRunEventRepository eventRepository;
    private final InMemoryAgentRunEventBus eventBus;
    private final AgentEventPayloadResolver payloadResolver;
    private final AgentPartialMessageCheckpointStore partialMessages;
    private final ObjectMapper objectMapper;
    private final AtomicLong streamIds = new AtomicLong();
    private final Map<Long, StreamConnection> activeStreams = new ConcurrentHashMap<>();

    public AgentRunEventStreamService(AgentRunEventRepository eventRepository,
                                      InMemoryAgentRunEventBus eventBus,
                                      AgentEventPayloadResolver payloadResolver,
                                      AgentPartialMessageCheckpointStore partialMessages,
                                      ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.eventBus = eventBus;
        this.payloadResolver = payloadResolver;
        this.partialMessages = partialMessages;
        this.objectMapper = objectMapper;
    }

    public SseEmitter openStream(Long runId, Long after) {
        long cursor = after == null ? 0L : Math.max(0L, after);
        SseEmitter emitter = createEmitter();
        StreamConnection connection = new StreamConnection(
                streamIds.incrementAndGet(), runId, emitter, cursor);
        activeStreams.put(connection.streamId, connection);
        connection.unsubscribe = eventBus.subscribeWithRedis(runId, event -> onBusEvent(connection, event));
        emitter.onCompletion(() -> close(connection));
        emitter.onTimeout(() -> close(connection));
        emitter.onError(ignored -> close(connection));
        if (!sendHeartbeat(connection)) return emitter;
        sendPartialSnapshot(connection);
        poll(connection);
        return emitter;
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter(SSE_TIMEOUT_MS);
    }

    @Scheduled(fixedDelayString = "${penmate.agent.stream-poll-ms:1000}")
    public void pollActiveStreams() {
        activeStreams.values().forEach(connection -> {
            if (System.currentTimeMillis() - connection.lastHeartbeatAt.get() >= SSE_HEARTBEAT_INTERVAL_MS) {
                if (!sendHeartbeat(connection)) return;
            }
            poll(connection);
        });
    }

    private boolean sendHeartbeat(StreamConnection connection) {
        synchronized (connection.monitor) {
            if (connection.closed.get()) return false;
            try {
                connection.emitter.send(SseEmitter.event().comment("keepalive"));
                connection.lastHeartbeatAt.set(System.currentTimeMillis());
                return true;
            } catch (IOException | IllegalStateException ex) {
                log.debug("agent run SSE heartbeat failed: runId={}", connection.runId, ex);
                connection.emitter.completeWithError(ex);
                close(connection);
                return false;
            }
        }
    }

    private void onBusEvent(StreamConnection connection, AgentEvent event) {
        if (event.sequence() == null || event.sequence() < 0) {
            synchronized (connection.monitor) {
                if (!connection.closed.get()) {
                    sendEvent(connection, event);
                }
            }
            return;
        }
        poll(connection);
    }

    private void sendPartialSnapshot(StreamConnection connection) {
        partialMessages.find(connection.runId).ifPresent(snapshot -> {
            synchronized (connection.monitor) {
                if (connection.closed.get() || snapshot.text().isBlank()) return;
                try {
                    sendEvent(connection, AgentEvent.forBroadcast(connection.runId, -1L, "message.snapshot",
                            objectMapper.writeValueAsString(Map.of(
                                    "schemaVersion", 1,
                                    "turnId", String.valueOf(snapshot.turnId()),
                                    "text", snapshot.text(),
                                    "offset", snapshot.offset(),
                                    "updatedAt", snapshot.updatedAt().toString()
                            ))));
                } catch (JsonProcessingException ex) {
                    log.warn("agent run partial snapshot serialization failed: runId={}", connection.runId, ex);
                }
            }
        });
    }

    private void poll(StreamConnection connection) {
        synchronized (connection.monitor) {
            if (connection.closed.get()) return;
            try {
                AgentEventWindow window = eventRepository.findWindow(connection.runId);
                if (window == null) {
                    throw new IllegalArgumentException("Agent run not found: " + connection.runId);
                }
                long cursor = connection.cursor.get();
                if (window.requiresResetAfter(cursor)) {
                    if (!sendReset(connection, cursor, window)) return;
                    connection.cursor.set(window.latestSequence());
                    cursor = window.latestSequence();
                }
                for (AgentEvent event : eventRepository.listAfter(connection.runId, cursor)) {
                    if (event.sequence() == null || event.sequence() <= connection.cursor.get()) continue;
                    AgentEvent resolved = payloadResolver.resolve(event);
                    if (!sendEvent(connection, resolved)) return;
                    connection.cursor.set(event.sequence());
                    if (isTerminal(resolved)) {
                        connection.emitter.complete();
                        close(connection);
                        return;
                    }
                }
            } catch (RuntimeException ex) {
                log.debug("agent run SSE poll failed: runId={}, cursor={}",
                        connection.runId, connection.cursor.get(), ex);
                connection.emitter.completeWithError(ex);
                close(connection);
            }
        }
    }

    private boolean sendEvent(StreamConnection connection, AgentEvent event) {
        try {
            AgentRunEventDto payload = toDto(event);
            connection.emitter.send(SseEmitter.event()
                    .name("agent.event")
                    .data(payload));
            SseEmitter.SseEventBuilder namedEvent = SseEmitter.event()
                    .name(event.eventType())
                    .data(payload);
            if (event.sequence() != null && event.sequence() >= 0) {
                namedEvent.id(stringify(event.sequence()));
            }
            connection.emitter.send(namedEvent);
            return true;
        } catch (IOException | IllegalStateException ex) {
            log.debug("agent run SSE send failed: runId={}, sequence={}, eventType={}",
                    event.runId(), event.sequence(), event.eventType(), ex);
            connection.emitter.completeWithError(ex);
            close(connection);
            return false;
        }
    }

    private boolean sendReset(StreamConnection connection, long requestedAfter, AgentEventWindow window) {
        try {
            connection.emitter.send(SseEmitter.event()
                    .id(stringify(window.latestSequence()))
                    .name("stream.reset")
                    .data(new AgentStreamResetDto(
                            stringify(connection.runId),
                            stringify(requestedAfter),
                            stringify(window.oldestHotSequence()),
                            stringify(window.latestSequence()),
                            "CURSOR_EXPIRED")));
            return true;
        } catch (IOException | IllegalStateException ex) {
            log.debug("agent run SSE reset failed: runId={}, cursor={}",
                    connection.runId, requestedAfter, ex);
            connection.emitter.completeWithError(ex);
            close(connection);
            return false;
        }
    }

    private void close(StreamConnection connection) {
        if (!connection.closed.compareAndSet(false, true)) return;
        activeStreams.remove(connection.streamId, connection);
        connection.unsubscribe.run();
    }

    private boolean isTerminal(AgentEvent event) {
        return event != null
                && ("run.completed".equals(event.eventType())
                || "run.failed".equals(event.eventType())
                || "run.cancelled".equals(event.eventType())
                || "run.superseded".equals(event.eventType()));
    }

    private AgentRunEventDto toDto(AgentEvent event) {
        return new AgentRunEventDto(
                stringify(event.eventId()),
                stringify(event.runId()),
                stringify(event.projectId()),
                stringify(event.sessionId()),
                stringify(event.turnId()),
                stringify(event.sequence()),
                event.schemaVersion(),
                event.eventType(),
                event.payloadJson(),
                event.createdAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(event.createdAt())
        );
    }

    private String stringify(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private static final class StreamConnection {
        private final long streamId;
        private final Long runId;
        private final SseEmitter emitter;
        private final AtomicLong cursor;
        private final AtomicLong lastHeartbeatAt = new AtomicLong();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Object monitor = new Object();
        private volatile Runnable unsubscribe = () -> { };

        private StreamConnection(long streamId, Long runId, SseEmitter emitter, long cursor) {
            this.streamId = streamId;
            this.runId = runId;
            this.emitter = emitter;
            this.cursor = new AtomicLong(cursor);
        }
    }
}
