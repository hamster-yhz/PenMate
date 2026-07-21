package com.penmate.backend.infrastructure.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.run.AgentPartialMessageCheckpointStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RedisAgentPartialMessageCheckpointStore implements AgentPartialMessageCheckpointStore {

    private static final String KEY_PREFIX = "agent:run:partial:";
    private static final Duration TTL = Duration.ofHours(24);

    private final Map<Long, Snapshot> localFallback = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisAgentPartialMessageCheckpointStore(StringRedisTemplate redisTemplate,
                                                   ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(Snapshot snapshot) {
        if (snapshot == null || snapshot.runId() == null) return;
        localFallback.put(snapshot.runId(), snapshot);
        try {
            redisTemplate.opsForValue().set(key(snapshot.runId()), objectMapper.writeValueAsString(snapshot), TTL);
        } catch (Exception ex) {
            log.debug("agent.partial.checkpoint.redis.save.failed: runId={}", snapshot.runId(), ex);
        }
    }

    @Override
    public Optional<Snapshot> find(Long runId) {
        if (runId == null) return Optional.empty();
        try {
            String json = redisTemplate.opsForValue().get(key(runId));
            if (json != null && !json.isBlank()) {
                Snapshot snapshot = objectMapper.readValue(json, Snapshot.class);
                localFallback.put(runId, snapshot);
                return Optional.of(snapshot);
            }
        } catch (Exception ex) {
            log.debug("agent.partial.checkpoint.redis.read.failed: runId={}", runId, ex);
        }
        return Optional.ofNullable(localFallback.get(runId));
    }

    @Override
    public void delete(Long runId) {
        if (runId == null) return;
        localFallback.remove(runId);
        try {
            redisTemplate.delete(key(runId));
        } catch (RuntimeException ex) {
            log.debug("agent.partial.checkpoint.redis.delete.failed: runId={}", runId, ex);
        }
    }

    private String key(Long runId) {
        return KEY_PREFIX + runId;
    }
}
