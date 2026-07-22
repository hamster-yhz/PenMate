package com.penmate.backend.application.agent.run;

public interface AgentRunDispatchRequestPublisher {

    void publish(AgentRunDispatchRequested request);
}
