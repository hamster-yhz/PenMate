package com.penmate.backend.application.agent.context;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ContextEpochSnapshotCache {
    private static final Duration TTL = Duration.ofMinutes(30);
    private final StringRedisTemplate redis;

    public ContextEpochSnapshotCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String get(Long epochId) {
        try { return redis.opsForValue().get(key(epochId)); } catch (RuntimeException ignored) { return null; }
    }

    public void put(Long epochId, String snapshot) {
        try { redis.opsForValue().set(key(epochId), snapshot, TTL); } catch (RuntimeException ignored) { }
    }

    private String key(Long epochId) {
        return "agent:context-epoch:" + epochId;
    }
}
