package com.penmate.backend.application.agent.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.application.agent.run.AgentEventArchiveService;
import com.penmate.backend.application.agent.run.AgentEventPayloadResolver;
import com.penmate.backend.application.agent.run.AgentPartialMessageCheckpointStore;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunHistoryQueryServiceTest {

    @Mock private AgentConversationAppService conversations;
    @Mock private AgentRunRepository runs;
    @Mock private AgentRunEventRepository events;
    @Mock private AgentEventArchiveService archives;
    @Mock private AgentPartialMessageCheckpointStore partialMessages;
    @Mock private AgentEventPayloadResolver payloadResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returns_all_runs_with_persisted_events_after_ownership_check() {
        AgentRun run = new AgentRun(40L, 10L, 20L, 30L, 50L,
                "DONE", "completed", null, null, 2L, null, "trace", null, null);
        AgentEvent event = AgentEvent.replay(60L, 40L, 1L, "run.started", "{\"phase\":\"created\"}");
        when(runs.listBySession(10L, 20L)).thenReturn(List.of(run));
        when(archives.readArchived(40L)).thenReturn(List.of());
        when(events.listAfter(40L, 0L)).thenReturn(List.of(event));
        when(partialMessages.find(40L)).thenReturn(Optional.empty());
        AgentRunHistoryQueryService service = service();

        var result = service.list(10L, 20L, 50L);

        verify(conversations).requireOwned(10L, 20L, 50L, false);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().events()).extracting(AgentEvent::eventType).containsExactly("run.started");
    }

    @Test
    void returns_only_the_exact_running_run_partial_output() {
        AgentRun run = new AgentRun(40L, 10L, 20L, 30L, 50L,
                "RUNNING", "executing", null, null, 2L, null, "trace", null, null);
        Instant updatedAt = Instant.parse("2026-07-21T10:00:00Z");
        when(runs.listBySession(10L, 20L)).thenReturn(List.of(run));
        when(archives.readArchived(40L)).thenReturn(List.of());
        when(events.listAfter(40L, 0L)).thenReturn(List.of());
        when(partialMessages.find(40L)).thenReturn(Optional.of(
                new AgentPartialMessageCheckpointStore.Snapshot(40L, 30L, "current partial", 15L, updatedAt)));

        var output = service().list(10L, 20L, 50L).getFirst().output();

        assertThat(output).isEqualTo(new AgentRunHistoryQueryService.RunOutput(
                "current partial", 15L, null, "partial", updatedAt));
    }

    @Test
    void returns_persisted_final_output_for_its_run() {
        AgentRun run = new AgentRun(40L, 10L, 20L, 30L, 50L,
                "DONE", "completed", null, null, 3L, null, "trace", null, null);
        Instant createdAt = Instant.parse("2026-07-21T10:00:03Z");
        AgentEvent completed = new AgentEvent(60L, 40L, 10L, 20L, 30L, 2L, 1,
                "message.completed", "{\"text\":\"final answer\"}", createdAt);
        when(runs.listBySession(10L, 20L)).thenReturn(List.of(run));
        when(archives.readArchived(40L)).thenReturn(List.of());
        when(events.listAfter(40L, 0L)).thenReturn(List.of(completed));
        when(payloadResolver.resolve(completed)).thenReturn(completed);

        var output = service().list(10L, 20L, 50L).getFirst().output();

        assertThat(output).isEqualTo(new AgentRunHistoryQueryService.RunOutput(
                "final answer", 12L, 2L, "final", createdAt));
    }

    private AgentRunHistoryQueryService service() {
        return new AgentRunHistoryQueryService(
                conversations, runs, events, archives, partialMessages, payloadResolver, objectMapper);
    }
}
