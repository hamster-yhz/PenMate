package com.penmate.backend.application.agent.run;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentRunDispatchRequestedListenerTest {

    @Test
    void forwards_committed_dispatch_request_to_dispatcher() {
        AgentRunDispatcher dispatcher = mock(AgentRunDispatcher.class);
        AgentRunDispatchRequestedListener listener = new AgentRunDispatchRequestedListener(dispatcher);

        listener.on(new AgentRunDispatchRequested(61L, "trace-2"));

        verify(dispatcher).dispatchInitialRun(61L, "trace-2");
    }
}
