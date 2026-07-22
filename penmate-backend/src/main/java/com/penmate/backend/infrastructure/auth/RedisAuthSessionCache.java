package com.penmate.backend.infrastructure.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.auth.support.AuthSessionCache;
import com.penmate.backend.application.auth.support.AuthTokenBundle;
import com.penmate.backend.application.auth.support.AuthTokenFingerprint;
import com.penmate.backend.application.auth.support.AuthUserSessionPayload;
import com.penmate.backend.application.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Component
public class RedisAuthSessionCache implements AuthSessionCache {

    private static final String ACCESS_PREFIX = "auth:access:";
    private static final String REFRESH_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisAuthSessionCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = Objects.requireNonNull(redis, "stringRedisTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void saveSession(AuthUserSessionPayload payload, AuthTokenBundle bundle) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(bundle, "bundle must not be null");
        Objects.requireNonNull(bundle.accessJti(), "accessJti must not be null");
        Objects.requireNonNull(bundle.refreshJti(), "refreshJti must not be null");
        Objects.requireNonNull(bundle.accessExpiresAt(), "accessExpiresAt must not be null");
        String json = toJson(payload);
        redis.opsForValue().set(ACCESS_PREFIX + bundle.accessJti().trim(), json,
                Duration.between(Instant.now(), bundle.accessExpiresAt()));
        redis.opsForValue().set(refreshKey(bundle.refreshJti()), json,
                Duration.between(Instant.now(), bundle.refreshExpiresAt()));
    }

    @Override
    public AuthUserSessionPayload getByAccessJti(String accessJti) {
        Objects.requireNonNull(accessJti, "accessJti must not be null");
        return parse(redis.opsForValue().get(ACCESS_PREFIX + accessJti.trim()));
    }

    @Override
    public AuthUserSessionPayload getByRefreshJti(String refreshJti) {
        Objects.requireNonNull(refreshJti, "refreshJti must not be null");
        return parse(redis.opsForValue().get(refreshKey(refreshJti)));
    }

    @Override
    public void revokeAccess(String accessJti) {
        Objects.requireNonNull(accessJti, "accessJti must not be null");
        redis.delete(ACCESS_PREFIX + accessJti.trim());
    }

    @Override
    public void revokeRefresh(String refreshJti) {
        Objects.requireNonNull(refreshJti, "refreshJti must not be null");
        redis.delete(refreshKey(refreshJti));
    }

    @Override
    public void revokeRefreshFingerprint(String refreshJtiHash) {
        Objects.requireNonNull(refreshJtiHash, "refreshJtiHash must not be null");
        redis.delete(REFRESH_PREFIX + refreshJtiHash.trim());
    }

    @Override
    public void updateSessionPayload(String accessJti, AuthUserSessionPayload payload) {
        updatePayload(ACCESS_PREFIX + accessJti.trim(), payload);
        if (payload.getRefreshJti() != null && !payload.getRefreshJti().isBlank()) {
            updatePayload(refreshKey(payload.getRefreshJti()), payload);
        }
    }

    private String refreshKey(String refreshJti) {
        return REFRESH_PREFIX + AuthTokenFingerprint.sha256(refreshJti);
    }

    private void updatePayload(String key, AuthUserSessionPayload payload) {
        Long ttlSeconds = redis.getExpire(key);
        if (ttlSeconds != null && ttlSeconds > 0) {
            redis.opsForValue().set(key, toJson(payload), Duration.ofSeconds(ttlSeconds));
        }
    }

    private String toJson(AuthUserSessionPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw BusinessException.of("Failed to cache auth session");
        }
    }

    private AuthUserSessionPayload parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, AuthUserSessionPayload.class);
        } catch (JsonProcessingException ex) {
            throw BusinessException.of("Failed to parse auth session");
        }
    }
}
