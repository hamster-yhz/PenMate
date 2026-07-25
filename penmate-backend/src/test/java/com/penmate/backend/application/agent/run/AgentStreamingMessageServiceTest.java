package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.llm.AgentLlmStreamEvent;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentStreamingMessageServiceTest {

    @Test
    void publishes_offset_deltas_and_saves_a_cumulative_checkpoint() {
        AgentRunEventPublisher events = mock(AgentRunEventPublisher.class);
        AgentPartialMessageCheckpointStore checkpoints = mock(AgentPartialMessageCheckpointStore.class);
        AgentStreamingMessageService.StreamSession stream = new AgentStreamingMessageService(events, checkpoints)
                .open(7L, 8L, 1, "");

        stream.accept("Hel");
        stream.accept("lo");
        stream.complete("Hello");

        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeastOnce()).broadcastOnly(eq(7L), eq("message.delta"), payloads.capture(), eq(-1L));
        assertThat(payloads.getAllValues().stream()
                .map(value -> String.valueOf(((Map<?, ?>) value).get("text"))).toList())
                .containsExactly("Hel", "lo");
        assertThat(payloads.getAllValues().stream()
                .map(value -> ((Number) ((Map<?, ?>) value).get("offset")).intValue()).toList())
                .containsExactly(0, 3);
        assertThat(payloads.getAllValues())
                .allSatisfy(value -> assertThat(((Map<?, ?>) value).get("channel")).isEqualTo("final"));

        ArgumentCaptor<AgentPartialMessageCheckpointStore.Snapshot> snapshots =
                ArgumentCaptor.forClass(AgentPartialMessageCheckpointStore.Snapshot.class);
        verify(checkpoints, atLeastOnce()).save(snapshots.capture());
        assertThat(snapshots.getAllValues().getLast().text()).isEqualTo("Hello");
        assertThat(snapshots.getAllValues().getLast().offset()).isEqualTo(5L);
    }

    @Test
    void keeps_public_process_blocks_out_of_the_final_message_stream() {
        AgentRunEventPublisher events = mock(AgentRunEventPublisher.class);
        AgentPartialMessageCheckpointStore checkpoints = mock(AgentPartialMessageCheckpointStore.class);
        AgentStreamingMessageService.StreamSession stream = new AgentStreamingMessageService(events, checkpoints)
                .open(7L, 8L, 2, "");

        stream.acceptEvent(new AgentLlmStreamEvent.CommentaryDelta("正在读取设定"));
        stream.acceptEvent(new AgentLlmStreamEvent.ReasoningSummaryDelta("发现时间线冲突"));
        stream.acceptEvent(new AgentLlmStreamEvent.OutputTextDelta("最终回答"));
        stream.complete(new AgentLlmTurnResponse("stop", "最终回答", java.util.List.of(), "{}",
                null, "正在读取设定", "发现时间线冲突", java.util.List.of()));

        verify(events).publish(eq(7L), eq("model.commentary.completed"),
                org.mockito.ArgumentMatchers.argThat(value -> value.toString().contains("正在读取设定")));
        verify(events).publish(eq(7L), eq("model.reasoning_summary.completed"),
                org.mockito.ArgumentMatchers.argThat(value -> value.toString().contains("发现时间线冲突")));
        verify(events, atLeastOnce()).broadcastOnly(eq(7L), eq("message.delta"),
                org.mockito.ArgumentMatchers.argThat(value -> value.toString().contains("最终回答")), eq(-1L));
    }
}
