package com.penmate.backend.infrastructure.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.run.AgentRunEventBus;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class InMemoryAgentRunEventBus implements AgentRunEventBus {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAgentRunEventBus.class);
    private static final String REDIS_CHANNEL_PREFIX = "agent:run:event:";

    private final Map<Long, List<Consumer<AgentEvent>>> subscribers = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer listenerContainer;

    public InMemoryAgentRunEventBus(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.listenerContainer = listenerContainer;
    }

    @Override
    public void publish(AgentEvent event) {
        subscribers.getOrDefault(event.runId(), List.of())
                .forEach(consumer -> consumer.accept(event));
        try {
            String channel = REDIS_CHANNEL_PREFIX + event.runId();
            String message = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, message);
        } catch (Exception ex) {
            log.warn("Redis pub/sub publish failed: runId={}", event.runId(), ex);
        }
    }

    public Runnable subscribe(Long runId, Consumer<AgentEvent> consumer) {
        subscribers.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(consumer);
        return () -> subscribers.getOrDefault(runId, List.of()).remove(consumer);
    }

    public Runnable subscribeWithRedis(Long runId, Consumer<AgentEvent> consumer) {
        Runnable localUnsub = subscribe(runId, consumer);
        String channel = REDIS_CHANNEL_PREFIX + runId;
        MessageListener listener = (message, pattern) -> {
            try {
                AgentEvent event = objectMapper.readValue(message.getBody(), AgentEvent.class);
                consumer.accept(event);
            } catch (Exception ex) {
                log.warn("Failed to deserialize Redis pub/sub event: runId={}", runId, ex);
            }
        };
        listenerContainer.addMessageListener(listener, new ChannelTopic(channel));
        return () -> {
            localUnsub.run();
            listenerContainer.removeMessageListener(listener);
        };
    }
}