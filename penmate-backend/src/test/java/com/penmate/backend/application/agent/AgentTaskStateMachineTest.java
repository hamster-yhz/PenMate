package com.penmate.backend.application.agent;

import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import org.junit.jupiter.api.Test;

class AgentTaskStateMachineTest {

    private final AgentTaskStateMachine stateMachine = new AgentTaskStateMachine();

    @Test
    void UT_APP_AGENT_TASK_STATE_MACHINE_ALLOWS_WAITING_APPROVAL_TO_RUNNING_FOR_PHASE_B_REENTRY() {
        stateMachine.assertTransition(AgentTaskStatus.WAITING_APPROVAL.value(), AgentTaskStatus.RUNNING);
    }
}
