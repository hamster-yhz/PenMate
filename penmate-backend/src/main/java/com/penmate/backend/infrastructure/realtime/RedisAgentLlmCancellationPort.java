package com.penmate.backend.infrastructure.realtime;

import com.penmate.backend.application.agent.llm.AgentLlmCancellationPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class RedisAgentLlmCancellationPort implements AgentLlmCancellationPort {

    private static final String CHANNEL = "agent:run:cancel";

    private final Map<Long, List<LocalRegistration>> registrations = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;

    public RedisAgentLlmCancellationPort(StringRedisTemplate redisTemplate,
                                         RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
    }

    @PostConstruct
    void subscribe() {
        MessageListener listener = (message, pattern) -> {
            try {
                cancelLocal(Long.valueOf(new String(message.getBody(), StandardCharsets.UTF_8)));
            } catch (RuntimeException ex) {
                log.warn("agent.llm.cancel.message.invalid", ex);
            }
        };
        listenerContainer.addMessageListener(listener, new ChannelTopic(CHANNEL));
    }

    @Override
    public Registration register(Long runId, Runnable cancelAction) {
        LocalRegistration registration = new LocalRegistration(runId, cancelAction);
        registrations.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(registration);
        return registration;
    }

    @Override
    public void cancel(Long runId) {
        if (runId == null) return;
        cancelLocal(runId);
        try {
            redisTemplate.convertAndSend(CHANNEL, String.valueOf(runId));
        } catch (RuntimeException ex) {
            log.warn("agent.llm.cancel.publish.failed: runId={}", runId, ex);
        }
    }

    private void cancelLocal(Long runId) {
        registrations.getOrDefault(runId, List.of()).forEach(LocalRegistration::cancel);
    }

    private final class LocalRegistration implements Registration {
        private final Long runId;
        private final Runnable cancelAction;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private LocalRegistration(Long runId, Runnable cancelAction) {
            this.runId = runId;
            this.cancelAction = cancelAction == null ? () -> { } : cancelAction;
        }

        private void cancel() {
            if (cancelled.compareAndSet(false, true)) cancelAction.run();
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            List<LocalRegistration> current = registrations.get(runId);
            if (current == null) return;
            current.remove(this);
            if (current.isEmpty()) registrations.remove(runId, current);
        }
    }
}
