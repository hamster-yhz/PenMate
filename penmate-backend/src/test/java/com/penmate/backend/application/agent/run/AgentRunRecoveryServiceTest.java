package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunRecoveryServiceTest {

    @Test
    void should_replay_durable_events_after_the_latest_checkpoint() {
        AgentCheckpointService checkpoints = mock(AgentCheckpointService.class);
        AgentRunEventRepository events = mock(AgentRunEventRepository.class);
        AgentRuntimeStateReducer reducer = reducer();
        AgentRunRecoveryService service = new AgentRunRecoveryService(checkpoints, events, reducer);
        AgentRuntimeState checkpoint = AgentRuntimeState.empty(70001L)
                .withStatusAndPhase("RUNNING", "executing", 5L);
        when(checkpoints.loadLatest(70001L)).thenReturn(checkpoint);
        when(events.listAfter(70001L, 5L)).thenReturn(List.of(
                AgentEvent.replay(6L, 70001L, 6L, "approval.requested", "{\"approvalId\":88001}"),
                AgentEvent.replay(7L, 70001L, 7L, "run.waiting_approval", "{\"approvalId\":88001}")
        ));

        AgentRuntimeState recovered = service.recover(70001L);

        assertThat(recovered.lastEventSeq()).isEqualTo(7L);
        assertThat(recovered.activeApprovalId()).isEqualTo(88001L);
        verify(events).listAfter(70001L, 5L);
    }

    @Test
    void should_replay_from_zero_when_no_durable_checkpoint_exists() {
        AgentCheckpointService checkpoints = mock(AgentCheckpointService.class);
        AgentRunEventRepository events = mock(AgentRunEventRepository.class);
        AgentRunRecoveryService service = new AgentRunRecoveryService(
                checkpoints, events, reducer());
        when(checkpoints.loadLatest(70001L)).thenReturn(null);
        when(events.listAfter(70001L, 0L)).thenReturn(List.of(
                AgentEvent.replay(1L, 70001L, 1L, "run.started", "{\"phase\":\"routing\"}")
        ));

        assertThat(service.recover(70001L).status()).isEqualTo("RUNNING");
        verify(events).listAfter(70001L, 0L);
    }

    private AgentRuntimeStateReducer reducer() {
        return new AgentRuntimeStateReducer(new JacksonJsonCodec(new ObjectMapper()));
    }
}
