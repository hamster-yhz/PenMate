package com.penmate.backend.infrastructure.agent.run;

import com.penmate.backend.application.agent.run.AgentRunDispatcher;
import com.penmate.backend.application.agent.run.AgentRunDispatchRequested;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AgentRunDispatchRequestedListener {

    private final AgentRunDispatcher dispatcher;

    public AgentRunDispatchRequestedListener(AgentRunDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AgentRunDispatchRequested event) {
        dispatcher.dispatchInitialRun(event.runId(), event.traceId());
    }
}
