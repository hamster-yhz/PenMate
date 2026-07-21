package com.penmate.backend.application.agent.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.application.agent.run.AgentEventArchiveService;
import com.penmate.backend.application.agent.run.AgentEventPayloadResolver;
import com.penmate.backend.application.agent.run.AgentPartialMessageCheckpointStore;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.util.Objects;

@Service
public class AgentRunHistoryQueryService {

    private final AgentConversationAppService conversations;
    private final AgentRunRepository runs;
    private final AgentRunEventRepository events;
    private final AgentEventArchiveService archives;
    private final AgentPartialMessageCheckpointStore partialMessages;
    private final AgentEventPayloadResolver payloadResolver;
    private final ObjectMapper objectMapper;

    public AgentRunHistoryQueryService(AgentConversationAppService conversations,
                                       AgentRunRepository runs,
                                       AgentRunEventRepository events,
                                       AgentEventArchiveService archives,
                                       AgentPartialMessageCheckpointStore partialMessages,
                                       AgentEventPayloadResolver payloadResolver,
                                       ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.runs = runs;
        this.events = events;
        this.archives = archives;
        this.partialMessages = partialMessages;
        this.payloadResolver = payloadResolver;
        this.objectMapper = objectMapper;
    }

    public List<RunHistory> list(Long projectId, Long sessionId, Long userId) {
        conversations.requireOwned(projectId, sessionId, userId, false);
        return runs.listBySession(projectId, sessionId).stream()
                .map(run -> {
                    List<AgentEvent> runEvents = allEvents(run.runId());
                    return new RunHistory(run, resolveOutput(run, runEvents), runEvents);
                })
                .toList();
    }

    private RunOutput resolveOutput(AgentRun run, List<AgentEvent> runEvents) {
        boolean terminal = run.status().isTerminal();
        if (!terminal) {
            RunOutput partial = partialOutput(run);
            if (partial != null) return partial;
        }

        RunOutput persisted = null;
        for (int index = runEvents.size() - 1; index >= 0; index--) {
            AgentEvent event = runEvents.get(index);
            if ("message.completed".equals(event.eventType()) || "message.interrupted".equals(event.eventType())) {
                persisted = eventOutput(
                        event, "message.completed".equals(event.eventType()) ? "final" : "interrupted");
                if (persisted != null) break;
            }
        }

        if (persisted == null && "FAILED".equalsIgnoreCase(run.runStatus())) {
            for (int index = runEvents.size() - 1; index >= 0; index--) {
                AgentEvent event = runEvents.get(index);
                if (!"run.failed".equals(event.eventType())) continue;
                persisted = failedOutput(event);
                if (persisted != null) break;
            }
        }
        RunOutput partial = partialOutput(run);
        if (persisted == null) return partial;
        if (partial == null || persisted.updatedAt() == null || partial.updatedAt() == null) return persisted;
        return partial.updatedAt().isAfter(persisted.updatedAt()) ? partial : persisted;
    }

    private RunOutput partialOutput(AgentRun run) {
        return partialMessages.find(run.runId())
                .filter(snapshot -> Objects.equals(snapshot.turnId(), run.turnId()))
                .filter(snapshot -> !snapshot.text().isBlank())
                .map(snapshot -> new RunOutput(
                        snapshot.text(), snapshot.offset(), null, "partial", snapshot.updatedAt()))
                .orElse(null);
    }

    private RunOutput eventOutput(AgentEvent event, String state) {
        JsonNode payload = payload(event);
        String text = text(payload, "text");
        if (text.isBlank()) return null;
        long offset = payload.path("offset").canConvertToLong() ? payload.path("offset").asLong() : text.length();
        return new RunOutput(text, Math.max(offset, 0L), event.sequence(), state, event.createdAt());
    }

    private RunOutput failedOutput(AgentEvent event) {
        JsonNode payload = payload(event);
        String text = text(payload, "outputText");
        if (text.isBlank() && !payload.hasNonNull("errorMessage")) text = text(payload, "message");
        if (text.isBlank()) return null;
        return new RunOutput(text, (long) text.length(), event.sequence(), "interrupted", event.createdAt());
    }

    private JsonNode payload(AgentEvent event) {
        try {
            return objectMapper.readTree(payloadResolver.resolve(event).payloadJson());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Run output event payload", ex);
        }
    }

    private String text(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private List<AgentEvent> allEvents(Long runId) {
        LinkedHashMap<Long, AgentEvent> bySequence = new LinkedHashMap<>();
        archives.readArchived(runId).stream()
                .sorted(Comparator.comparing(AgentEvent::sequence))
                .forEach(event -> bySequence.put(event.sequence(), event));
        events.listAfter(runId, 0L).stream()
                .sorted(Comparator.comparing(AgentEvent::sequence))
                .forEach(event -> bySequence.put(event.sequence(), event));
        return List.copyOf(bySequence.values());
    }

    public record RunOutput(String text, Long offset, Long sequence, String state, Instant updatedAt) { }

    public record RunHistory(AgentRun run, RunOutput output, List<AgentEvent> events) { }
}
