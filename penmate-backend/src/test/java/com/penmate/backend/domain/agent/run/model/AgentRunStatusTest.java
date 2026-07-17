package com.penmate.backend.domain.agent.run.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunStatusTest {

    @Test
    void terminal_states_never_transition() {
        for (AgentRunStatus status : new AgentRunStatus[]{AgentRunStatus.DONE, AgentRunStatus.FAILED,
                AgentRunStatus.CANCELLED, AgentRunStatus.SUPERSEDED}) {
            assertThat(status.isTerminal()).isTrue();
            assertThat(status.canTransitionTo(AgentRunStatus.RUNNING)).isFalse();
        }
    }

    @Test
    void recoverable_states_have_explicit_transitions() {
        assertThat(AgentRunStatus.PENDING.canTransitionTo(AgentRunStatus.RUNNING)).isTrue();
        assertThat(AgentRunStatus.RUNNING.canTransitionTo(AgentRunStatus.SUSPENDED)).isTrue();
        assertThat(AgentRunStatus.WAITING_APPROVAL.canTransitionTo(AgentRunStatus.RUNNING)).isTrue();
        assertThat(AgentRunStatus.SUSPENDED.canTransitionTo(AgentRunStatus.SUPERSEDED)).isTrue();
    }
}
