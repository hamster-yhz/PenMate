package com.penmate.backend.domain.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.run.AgentRuntimeStateReducer;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRuntimeStateReducerContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentRuntimeStateReducer reducer = new AgentRuntimeStateReducer();

    @Test
    void applies_run_tool_message_and_approval_events() {
        AgentRuntimeState state = AgentRuntimeState.empty(70001L);

        // Apply events one by one and check intermediate states
        AgentRuntimeState s1 = reducer.apply(state, event(1L, "run.started", "{\"phase\":\"routing\"}"));
        assertThat(s1.status()).isEqualTo("RUNNING");
        assertThat(s1.phase()).isEqualTo("routing");
        assertThat(s1.lastEventSeq()).isEqualTo(1L);

        AgentRuntimeState s2 = reducer.apply(s1, event(2L, "tool.call.started", "{\"toolCallId\":\"call-1\",\"toolCode\":\"draft_generation\"}"));
        assertThat(s2.phase()).isEqualTo("routing");
        assertThat(s2.lastEventSeq()).isEqualTo(2L);

        AgentRuntimeState s3 = reducer.apply(s2, event(3L, "tool.call.waiting_approval", "{\"toolCallId\":\"call-1\",\"approvalId\":88001}"));
        assertThat(s3.activeApprovalId()).isEqualTo(88001L);
        assertThat(s3.lastEventSeq()).isEqualTo(3L);

        AgentRuntimeState s4 = reducer.apply(s3, event(4L, "message.delta", "{\"text\":\"abc\"}"));
        assertThat(s4.assistantDraft()).isEqualTo("abc");
        assertThat(s4.lastEventSeq()).isEqualTo(4L);
    }

    @Test
    void applyAll_returns_fully_reduced_state() {
        AgentRuntimeState state = AgentRuntimeState.empty(70001L);
        AgentRuntimeState reduced = reducer.applyAll(state, List.of(
                event(1L, "run.started", "{\"phase\":\"routing\"}"),
                event(2L, "tool.call.started", "{\"toolCallId\":\"call-1\",\"toolCode\":\"draft_generation\"}"),
                event(3L, "tool.call.waiting_approval", "{\"toolCallId\":\"call-1\",\"approvalId\":88001}"),
                event(4L, "message.delta", "{\"text\":\"abc\"}"),
                event(5L, "llm.continuation.saved", "{\"artifactId\":99001}"),
                event(6L, "message.completed", "{\"text\":\"abc\"}")
        ));

        assertThat(reduced).isNotNull();
        assertThat(reduced.runId()).isEqualTo(70001L);
        assertThat(reduced.phase()).isEqualTo("routing");
        assertThat(reduced.activeApprovalId()).isEqualTo(88001L);
        assertThat(reduced.assistantDraft()).isEqualTo("abc");
        assertThat(reduced.artifactRefs()).containsExactly(99001L);
        assertThat(reduced.assistantMessageCompleted()).isTrue();
        assertThat(reduced.lastEventSeq()).isEqualTo(6L);
    }

    private AgentEvent event(Long sequence, String eventType, String payloadJson) {
        return AgentEvent.replay(sequence, 70001L, sequence, eventType, payloadJson);
    }
}
