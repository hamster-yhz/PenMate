package com.penmate.backend.domain.agent.service;

import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTaskTransitionPolicyTest {

    private final AgentTaskTransitionPolicy policy = new AgentTaskTransitionPolicy();

    @Test
    void UT_DOMAIN_AGENT_TASK_TRANSITION_POLICY_ALLOWS_PENDING_TO_RUNNING() {
        policy.assertTransition(AgentTaskStatus.PENDING.value(), AgentTaskStatus.RUNNING);
    }

    @Test
    void UT_DOMAIN_AGENT_TASK_TRANSITION_POLICY_ALLOWS_RUNNING_TO_WAITING_APPROVAL() {
        policy.assertTransition(AgentTaskStatus.RUNNING.value(), AgentTaskStatus.WAITING_APPROVAL);
    }

    @Test
    void UT_DOMAIN_AGENT_TASK_TRANSITION_POLICY_REJECTS_DONE_TO_RUNNING() {
        assertThatThrownBy(() -> policy.assertTransition(AgentTaskStatus.DONE.value(), AgentTaskStatus.RUNNING))
                .isInstanceOf(InvalidAgentTaskTransitionException.class)
                .hasMessageContaining("Invalid generation task state transition");
    }

    @Test
    void UT_DOMAIN_AGENT_TASK_TRANSITION_POLICY_REJECTS_INVALID_RAW_STATUS() {
        assertThatThrownBy(() -> policy.parseStatus("unknown_status"))
                .isInstanceOf(InvalidAgentTaskTransitionException.class)
                .hasMessageContaining("Invalid generation task state transition");
    }
}
