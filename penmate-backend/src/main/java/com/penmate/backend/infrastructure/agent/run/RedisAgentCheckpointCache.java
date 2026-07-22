package com.penmate.backend.infrastructure.agent.run;

import com.penmate.backend.application.agent.run.AgentCheckpointCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisAgentCheckpointCache implements AgentCheckpointCache {

    private static final String KEY_PREFIX = "agent:checkpoint:v2:";
    private final StringRedisTemplate redis;

    public RedisAgentCheckpointCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(Long runId, String serializedState, Duration ttl) {
        redis.opsForValue().set(key(runId), serializedState, ttl);
    }

    @Override
    public String get(Long runId) {
        return redis.opsForValue().get(key(runId));
    }

    @Override
    public void delete(Long runId) {
        redis.delete(key(runId));
    }

    private String key(Long runId) {
        return KEY_PREFIX + runId + ":latest";
    }
}
