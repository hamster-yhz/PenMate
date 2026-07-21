package com.penmate.backend.application.agent.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLlmInvocationServiceTest {

    private final AgentLlmTurnRequest request = new AgentLlmTurnRequest(List.of(), List.of(), "auto");
    private final AgentLlmExecutionConfig config = AgentLlmExecutionConfig.builder()
            .providerCode("openai-compatible").build();

    @Test
    void streams_text_through_the_single_invocation_entrypoint() {
        AgentLlmGateway gateway = mock(AgentLlmGateway.class);
        TestCancellationPort cancellations = new TestCancellationPort();
        when(gateway.supportsStreaming(config)).thenReturn(true);
        when(gateway.streamTurn(any(), any(), any())).thenAnswer(invocation -> {
            AgentLlmStreamObserver observer = invocation.getArgument(2);
            observer.onResponseStarted();
            observer.onTextDelta("Hel");
            observer.onTextDelta("lo");
            return new AgentLlmTurnResponse("stop", "Hello", List.of(), "{}");
        });

        StringBuilder streamed = new StringBuilder();
        AgentLlmTurnResponse response = new AgentLlmInvocationService(gateway, cancellations)
                .invokeStreaming(7L, request, config, streamed::append);

        assertThat(streamed).hasToString("Hello");
        assertThat(response.assistantText()).isEqualTo("Hello");
        verify(gateway, never()).generateTurn(any(), any());
    }

    @Test
    void falls_back_only_when_streaming_fails_before_provider_activity() {
        AgentLlmGateway gateway = mock(AgentLlmGateway.class);
        when(gateway.supportsStreaming(config)).thenReturn(true);
        when(gateway.streamTurn(any(), any(), any())).thenThrow(new IllegalStateException("stream unsupported"));
        when(gateway.generateTurn(request, config))
                .thenReturn(new AgentLlmTurnResponse("stop", "buffered", List.of(), "{}"));

        AgentLlmTurnResponse response = new AgentLlmInvocationService(gateway, new TestCancellationPort())
                .invokeStreaming(7L, request, config, ignored -> { });

        assertThat(response.assistantText()).isEqualTo("buffered");
        verify(gateway).generateTurn(request, config);
    }

    @Test
    void never_silently_retries_after_provider_activity_started() {
        AgentLlmGateway gateway = mock(AgentLlmGateway.class);
        when(gateway.supportsStreaming(config)).thenReturn(true);
        when(gateway.streamTurn(any(), any(), any())).thenAnswer(invocation -> {
            AgentLlmStreamObserver observer = invocation.getArgument(2);
            observer.onResponseStarted();
            observer.onTextDelta("partial");
            throw new IllegalStateException("connection lost");
        });

        assertThatThrownBy(() -> new AgentLlmInvocationService(gateway, new TestCancellationPort())
                .invokeStreaming(7L, request, config, ignored -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("connection lost");
        verify(gateway, never()).generateTurn(any(), any());
    }

    @Test
    void propagates_run_cancellation_to_the_upstream_request() {
        AgentLlmGateway gateway = mock(AgentLlmGateway.class);
        TestCancellationPort cancellations = new TestCancellationPort();
        AtomicBoolean upstreamCancelled = new AtomicBoolean();
        when(gateway.supportsStreaming(config)).thenReturn(true);
        when(gateway.streamTurn(any(), any(), any())).thenAnswer(invocation -> {
            AgentLlmStreamObserver observer = invocation.getArgument(2);
            observer.onCancellable(() -> upstreamCancelled.set(true));
            cancellations.cancel(7L);
            if (observer.isCancelled()) throw new AgentLlmInvocationCancelledException();
            return new AgentLlmTurnResponse("stop", "", List.of(), "{}");
        });

        assertThatThrownBy(() -> new AgentLlmInvocationService(gateway, cancellations)
                .invokeStreaming(7L, request, config, ignored -> { }))
                .isInstanceOf(AgentLlmInvocationCancelledException.class);
        assertThat(upstreamCancelled).isTrue();
        verify(gateway, never()).generateTurn(any(), any());
    }

    private static final class TestCancellationPort implements AgentLlmCancellationPort {
        private Runnable action = () -> { };

        @Override
        public Registration register(Long runId, Runnable cancelAction) {
            action = cancelAction;
            return () -> action = () -> { };
        }

        @Override
        public void cancel(Long runId) {
            action.run();
        }
    }
}
