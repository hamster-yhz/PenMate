package com.penmate.backend.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.iam.AuthorizationSnapshotCache;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisAuthorizationSnapshotCache implements AuthorizationSnapshotCache {
    private static final String KEY_PREFIX = "iam:authorization:snapshot:";
    private static final Duration TTL = Duration.ofHours(8);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisAuthorizationSnapshotCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public CachedAuthorizationSnapshot get(Long userId) {
        try {
            String json = redis.opsForValue().get(key(userId));
            return json == null ? null : objectMapper.readValue(json, CachedAuthorizationSnapshot.class);
        } catch (DataAccessException | JsonProcessingException ignored) {
            return null;
        }
    }

    @Override
    public void put(CachedAuthorizationSnapshot snapshot) {
        try {
            redis.opsForValue().set(key(snapshot.userId()), objectMapper.writeValueAsString(snapshot), TTL);
        } catch (DataAccessException | JsonProcessingException ignored) {
            // PostgreSQL remains authoritative; a cache outage must not grant access.
        }
    }

    @Override
    public void evict(Long userId) {
        try {
            redis.delete(key(userId));
        } catch (DataAccessException ignored) {
            // The PostgreSQL authorization version still invalidates stale entries.
        }
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
