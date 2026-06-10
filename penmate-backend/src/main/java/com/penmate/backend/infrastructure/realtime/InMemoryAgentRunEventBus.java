package com.penmate.backend.infrastructure.realtime;

import com.penmate.backend.application.agent.run.AgentRunEventBus;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class InMemoryAgentRunEventBus implements AgentRunEventBus {

    private final Map<Long, List<Consumer<AgentEvent>>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void publish(AgentEvent event) {
        subscribers.getOrDefault(event.runId(), List.of()).forEach(consumer -> consumer.accept(event));
    }

    public Runnable subscribe(Long runId, Consumer<AgentEvent> consumer) {
        subscribers.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(consumer);
        return () -> subscribers.getOrDefault(runId, List.of()).remove(consumer);
    }
}
