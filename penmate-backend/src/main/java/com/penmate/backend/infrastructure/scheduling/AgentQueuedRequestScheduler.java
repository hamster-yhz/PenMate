package com.penmate.backend.infrastructure.scheduling;

import com.penmate.backend.application.agent.usecase.AgentQueuedRequestExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentQueuedRequestScheduler {
    private final AgentQueuedRequestExecutor executor;

    @Scheduled(fixedDelayString = "${penmate.agent.queued-request-poll-delay:PT1S}")
    public void executeNext() {
        executor.executeNext();
    }
}
