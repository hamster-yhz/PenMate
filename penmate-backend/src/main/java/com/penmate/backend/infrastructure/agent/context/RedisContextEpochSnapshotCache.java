package com.penmate.backend.infrastructure.agent.context;

import com.penmate.backend.application.agent.context.ContextEpochSnapshotCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisContextEpochSnapshotCache implements ContextEpochSnapshotCache {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "agent:context-epoch:";
    private final StringRedisTemplate redis;

    public RedisContextEpochSnapshotCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String get(Long epochId) {
        try {
            return redis.opsForValue().get(KEY_PREFIX + epochId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public void put(Long epochId, String snapshot) {
        try {
            redis.opsForValue().set(KEY_PREFIX + epochId, snapshot, TTL);
        } catch (RuntimeException ignored) {
            // Cache failure must not block durable context resolution.
        }
    }
}
