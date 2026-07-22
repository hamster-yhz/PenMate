package com.penmate.backend.infrastructure.agent.run;

import com.penmate.backend.application.agent.run.AgentRunDispatchRequestPublisher;
import com.penmate.backend.application.agent.run.AgentRunDispatchRequested;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringAgentRunDispatchRequestPublisher implements AgentRunDispatchRequestPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringAgentRunDispatchRequestPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(AgentRunDispatchRequested request) {
        publisher.publishEvent(request);
    }
}
