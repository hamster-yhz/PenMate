package com.penmate.backend.infrastructure.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import com.penmate.backend.interfaces.api.agent.dto.AgentRunEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class AgentRunEventStreamService {

    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final AgentRunEventRepository eventRepository;
    private final InMemoryAgentRunEventBus eventBus;
    private final AgentArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;

    public AgentRunEventStreamService(AgentRunEventRepository eventRepository,
                                      InMemoryAgentRunEventBus eventBus,
                                      AgentArtifactRepository artifactRepository,
                                      ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.eventBus = eventBus;
        this.artifactRepository = artifactRepository;
        this.objectMapper = objectMapper;
    }

    public SseEmitter openStream(Long runId, Long after) {
        Long cursor = after == null ? 0L : after;
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Runnable unsubscribe = eventBus.subscribe(runId, event -> {
            if (event.sequence() != null && event.sequence() > cursor) {
                send(emitter, event);
            }
        });
        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(unsubscribe);
        emitter.onError(ignored -> unsubscribe.run());
        for (AgentEvent event : eventRepository.listAfter(runId, cursor)) {
            AgentEvent resolved = resolveArtifact(event);
            send(emitter, resolved);
            if (isTerminal(resolved)) {
                emitter.complete();
                unsubscribe.run();
                return emitter;
            }
        }
        return emitter;
    }

    private AgentEvent resolveArtifact(AgentEvent event) {
        String payload = event.payloadJson();
        if (payload == null || !payload.contains("artifactRef")) {
            return event;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.has("artifactRef")) {
                Long artifactId = node.get("artifactRef").asLong();
                AgentArtifact artifact = artifactRepository.findById(artifactId);
                if (artifact != null && artifact.payloadJson() != null) {
                    return AgentEvent.forReplay(event, artifact.payloadJson());
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to resolve artifact ref: runId={}, sequence={}", event.runId(), event.sequence(), ex);
        }
        return event;
    }

    private void send(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(stringify(event.sequence()))
                    .name(event.eventType())
                    .data(toDto(event)));
            if (isTerminal(event)) {
                emitter.complete();
            }
        } catch (IOException | IllegalStateException ex) {
            log.debug("agent run SSE send failed: runId={}, sequence={}, eventType={}",
                    event.runId(), event.sequence(), event.eventType(), ex);
            emitter.completeWithError(ex);
        }
    }

    private boolean isTerminal(AgentEvent event) {
        return event != null
                && ("run.completed".equals(event.eventType())
                || "run.failed".equals(event.eventType())
                || "run.cancelled".equals(event.eventType()));
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
                event.createdAt() == null ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(event.createdAt())
        );
    }

    private String stringify(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}