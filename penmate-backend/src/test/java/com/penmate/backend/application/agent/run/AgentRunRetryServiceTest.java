package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunRetryServiceTest {

    private final AgentRunRepository runs = mock(AgentRunRepository.class);
    private final AgentRunSuccessorService successors = mock(AgentRunSuccessorService.class);
    private final AgentSessionRepository sessions = mock(AgentSessionRepository.class);
    private final AgentRepository agentRepository = mock(AgentRepository.class);
    private final AgentSkillActivationService skills = mock(AgentSkillActivationService.class);
    private final AgentRunRetryService service = new AgentRunRetryService(
            runs, successors, sessions, agentRepository, skills);

    @Test
    void locks_terminal_predecessor_and_creates_successor_from_immutable_input() {
        AgentRun predecessor = run(60L, null, "FAILED");
        AgentRun successor = run(61L, 60L, "PENDING");
        AgentRunInput input = input(60L);
        when(runs.findRunForUpdate(60L)).thenReturn(predecessor);
        when(runs.findInput(60L)).thenReturn(input);
        when(successors.create(predecessor, input, "trace-2")).thenReturn(61L);
        when(runs.findRun(61L)).thenReturn(successor);

        AgentRun result = service.retry(10L, 60L, 50L, List.of(), "trace-2");

        assertThat(result).isSameAs(successor);
        InOrder order = inOrder(runs, successors);
        order.verify(runs).findRunForUpdate(60L);
        order.verify(runs).findSuccessor(60L);
        order.verify(runs).findInput(60L);
        order.verify(successors).create(predecessor, input, "trace-2");
        order.verify(runs).findRun(61L);
    }

    @Test
    void repeated_retry_returns_existing_successor_without_dispatching_again() {
        AgentRun predecessor = run(60L, null, "CANCELLED");
        AgentRun successor = run(61L, 60L, "DONE");
        when(runs.findRunForUpdate(60L)).thenReturn(predecessor);
        when(runs.findSuccessor(60L)).thenReturn(successor);

        assertThat(service.retry(10L, 60L, 50L, List.of(), "trace-3")).isSameAs(successor);

        verify(runs, never()).findInput(60L);
        verify(successors, never()).create(predecessor, input(60L), "trace-3");
    }

    @Test
    void completed_run_is_eligible_for_reexecution() {
        AgentRun predecessor = run(60L, null, "DONE");
        AgentRun successor = run(61L, 60L, "PENDING");
        when(runs.findRunForUpdate(60L)).thenReturn(predecessor);
        when(runs.findInput(60L)).thenReturn(input(60L));
        when(successors.create(predecessor, input(60L), "trace-1")).thenReturn(61L);
        when(runs.findRun(61L)).thenReturn(successor);

        assertThat(service.retry(10L, 60L, 50L, List.of(), null)).isSameAs(successor);
    }

    @Test
    void rejects_non_terminal_run() {
        when(runs.findRunForUpdate(60L)).thenReturn(run(60L, null, "SUSPENDED"));

        assertThatThrownBy(() -> service.retry(10L, 60L, 50L, List.of(), "trace-2"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only terminal Agent Run can be retried");

        verify(successors, never()).create(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejects_retry_by_another_user() {
        when(runs.findRunForUpdate(60L)).thenReturn(run(60L, null, "FAILED"));

        assertThatThrownBy(() -> service.retry(10L, 60L, 99L, List.of(), "trace-2"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent Run belongs to another user");
    }

    private AgentRun run(Long runId, Long predecessorRunId, String status) {
        return new AgentRun(runId, 10L, 30L, 80L, 50L, predecessorRunId,
                status, status.toLowerCase(), null, null, null, null, 0L, 0,
                null, null, null, 0L, null, "trace-1", null, null);
    }

    private AgentRunInput input(Long runId) {
        return new AgentRunInput(runId, "continue", "WRITE", 40L,
                "selection", "style", "model", "plugins", "hash");
    }
}
