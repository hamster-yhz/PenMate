package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEvent;

public interface AgentRunEventBus {

    void publish(AgentEvent event);
}
