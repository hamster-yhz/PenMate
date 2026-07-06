package com.penmate.backend.application.agent.run;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AsyncAgentRunDispatcherTest {

    @Test
    void dispatchInitialRun_publishes_failed_event_when_executor_throws() {
        AgentRunExecutor executor = mock(AgentRunExecutor.class);
        AgentRunEventPublisher eventPublisher = mock(AgentRunEventPublisher.class);
        doThrow(new IllegalStateException("boom")).when(executor).execute(70001L, "trace-1");

        AsyncAgentRunDispatcher dispatcher = new AsyncAgentRunDispatcher(executor, eventPublisher);

        dispatcher.dispatchInitialRun(70001L, "trace-1");

        verify(eventPublisher).publish(eq(70001L), eq("run.failed"), any(Map.class));
    }
}
