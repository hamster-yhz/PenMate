package com.penmate.backend.infrastructure.realtime;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;

@Component
public class InMemoryAgentRunEventBus implements AgentRunEventBus {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAgentRunEventBus.class);
    private static final String REDIS_CHANNEL_PREFIX = "agent:run:event:";
    private final String instanceId = UUID.randomUUID().toString();

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
            String message = objectMapper.writeValueAsString(new RedisEventEnvelope(instanceId, event));
            redisTemplate.convertAndSend(channel, message);
        } catch (Exception ex) {
            log.warn("Redis pub/sub publish failed: runId={}", event.runId(), ex);
        }
    }

    public Runnable subscribe(Long runId, Consumer<AgentEvent> consumer) {
        List<Consumer<AgentEvent>> runSubscribers = subscribers.computeIfAbsent(
                runId, ignored -> new CopyOnWriteArrayList<>());
        runSubscribers.add(consumer);
        return () -> {
            runSubscribers.remove(consumer);
            if (runSubscribers.isEmpty()) subscribers.remove(runId, runSubscribers);
        };
    }

    public Runnable subscribeWithRedis(Long runId, Consumer<AgentEvent> consumer) {
        Runnable localUnsub = subscribe(runId, consumer);
        String channel = REDIS_CHANNEL_PREFIX + runId;
        MessageListener listener = (message, pattern) -> {
            try {
                JsonNode root = objectMapper.readTree(message.getBody());
                if (root.hasNonNull("originId") && instanceId.equals(root.path("originId").asText())) return;
                AgentEvent event = root.has("event")
                        ? objectMapper.treeToValue(root.get("event"), AgentEvent.class)
                        : objectMapper.treeToValue(root, AgentEvent.class);
                consumer.accept(event);
            } catch (Exception ex) {
                log.warn("Failed to deserialize Redis pub/sub event: runId={}", runId, ex);
            }
        };
        try {
            listenerContainer.addMessageListener(listener, new ChannelTopic(channel));
        } catch (RuntimeException ex) {
            log.warn("Redis pub/sub subscription failed, keeping local delivery: runId={}", runId, ex);
            return localUnsub;
        }
        return () -> {
            localUnsub.run();
            listenerContainer.removeMessageListener(listener);
        };
    }

    private record RedisEventEnvelope(String originId, AgentEvent event) {
    }
}
