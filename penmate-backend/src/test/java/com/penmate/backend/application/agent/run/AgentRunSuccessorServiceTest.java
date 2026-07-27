package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunSuccessorServiceTest {

    @Test
    void creates_new_run_with_cloned_input_and_rebinds_turn() {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
        AgentRunEventPublisher events = mock(AgentRunEventPublisher.class);
        AgentRunDispatchRequestPublisher dispatchRequests = mock(AgentRunDispatchRequestPublisher.class);
        when(ids.nextId()).thenReturn(61L);
        when(runs.insert(any())).thenReturn(1);
        when(runs.insertInput(any())).thenReturn(1);
        when(sessions.rebindTurnRun(30L, 80L, 60L, 61L)).thenReturn(1);
        when(sessions.updateLastRun(10L, 30L, 61L)).thenReturn(1);

        Long successorId = new AgentRunSuccessorService(runs, sessions, ids, events, dispatchRequests,
                mock(AgentSkillActivationService.class))
                .create(run(), input(), "trace-2");

        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        ArgumentCaptor<AgentRunInput> inputCaptor = ArgumentCaptor.forClass(AgentRunInput.class);
        verify(runs).insert(runCaptor.capture());
        verify(runs).insertInput(inputCaptor.capture());
        assertThat(successorId).isEqualTo(61L);
        assertThat(runCaptor.getValue().predecessorRunId()).isEqualTo(60L);
        assertThat(runCaptor.getValue().runStatus()).isEqualTo("PENDING");
        assertThat(inputCaptor.getValue().promptSnapshot()).isEqualTo("continue");
        verify(events).publish(eq(61L), eq("run.started"), any());
        verify(dispatchRequests).publish(new AgentRunDispatchRequested(61L, "trace-2"));
    }

    private AgentRun run() {
        return new AgentRun(60L, 10L, 30L, 80L, 50L,
                "RUNNING", "executing", 70L, null, 4L, null, "trace", null, null);
    }

    private AgentRunInput input() {
        return new AgentRunInput(60L, "continue", 40L, java.util.List.of(40L),
                "selection", "style", "model", "plugins", "STANDARD", "hash");
    }
}
