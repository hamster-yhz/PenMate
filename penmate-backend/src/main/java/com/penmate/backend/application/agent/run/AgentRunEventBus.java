package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEvent;

import java.util.function.Consumer;

public interface AgentRunEventBus {

    void publish(AgentEvent event);

    Runnable subscribe(Long runId, Consumer<AgentEvent> consumer);
}
