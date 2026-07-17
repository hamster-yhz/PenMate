package com.penmate.backend.application.agent.run;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class AgentRunDispatchRequestedListener {

    private final AgentRunDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(AgentRunDispatchRequested event) {
        dispatcher.dispatchInitialRun(event.runId(), event.traceId());
    }
}
