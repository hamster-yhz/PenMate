package com.penmate.backend.domain.agent.run;

import com.penmate.backend.application.agent.run.AgentRuntimeStateReducer;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRuntimeStateReducerContractTest {

    @Test
    void applies_run_tool_message_and_approval_events() {
        AgentRuntimeState state = AgentRuntimeState.empty(70001L);
        AgentRuntimeStateReducer reducer = new AgentRuntimeStateReducer();

        AgentRuntimeState reduced = reducer.applyAll(state, List.of(
                AgentEvent.replay(1L, 70001L, 1L, "run.started", "{\"phase\":\"created\"}"),
                AgentEvent.replay(2L, 70001L, 2L, "tool.call.started", "{\"toolCallId\":\"call-1\",\"toolCode\":\"draft_generation\"}"),
                AgentEvent.replay(3L, 70001L, 3L, "tool.call.waiting_approval", "{\"toolCallId\":\"call-1\",\"approvalId\":88001}"),
                AgentEvent.replay(4L, 70001L, 4L, "message.delta", "{\"text\":\"abc\"}")
        ));

        assertThat(reduced.runId()).isEqualTo(70001L);
        assertThat(reduced.phase()).isEqualTo("preflight");
        assertThat(reduced.activeApprovalId()).isEqualTo(88001L);
        assertThat(reduced.assistantDraft()).isEqualTo("abc");
        assertThat(reduced.lastEventSeq()).isEqualTo(4L);
    }
}
