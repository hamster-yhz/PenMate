package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import com.penmate.backend.domain.agent.run.repository.AgentCheckpointRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentCheckpointServiceTest {

    @Mock
    private AgentCheckpointRepository checkpointRepository;

    @Test
    void saves_checkpoint_for_policy_event_with_next_checkpoint_number() {
        when(checkpointRepository.findLatest(70001L))
                .thenReturn(new AgentCheckpoint(80000L, 70001L, 1L, 1L, "{}", 2, null));
        AgentCheckpointService service = new AgentCheckpointService(
                checkpointRepository,
                incrementingIds(81000L),
                new ObjectMapper()
        );
        AgentRuntimeState state = AgentRuntimeState.empty(70001L);

        service.checkpointIfNeeded(
                AgentEvent.replay(1L, 70001L, 2L, "tool.call.waiting_approval", "{\"approvalId\":88001}"),
                state
        );

        ArgumentCaptor<AgentCheckpoint> captor = ArgumentCaptor.forClass(AgentCheckpoint.class);
        verify(checkpointRepository).save(captor.capture());
        assertThat(captor.getValue().checkpointId()).isEqualTo(81001L);
        assertThat(captor.getValue().checkpointNo()).isEqualTo(2L);
        assertThat(captor.getValue().lastEventSeq()).isEqualTo(2L);
    }

    @Test
    void skips_checkpoint_when_policy_does_not_match() {
        AgentCheckpointService service = new AgentCheckpointService(
                checkpointRepository,
                incrementingIds(81000L),
                new ObjectMapper()
        );

        service.checkpointIfNeeded(
                AgentEvent.replay(2L, 70001L, 2L, "message.delta", "{\"text\":\"a\"}"),
                AgentRuntimeState.empty(70001L)
        );

        verify(checkpointRepository, never()).save(org.mockito.Mockito.any());
    }

    @Test
    void checkpoints_every_fifteenth_event() {
        AgentCheckpointService service = new AgentCheckpointService(
                checkpointRepository,
                incrementingIds(81000L),
                new ObjectMapper()
        );

        assertThat(service.shouldCheckpoint(
                AgentEvent.replay(15L, 70001L, 15L, "message.delta", "{\"text\":\"a\"}"),
                AgentRuntimeState.empty(70001L)
        )).isTrue();
    }

    private BusinessIdGenerator incrementingIds(long start) {
        AtomicLong next = new AtomicLong(start);
        return next::incrementAndGet;
    }
}
