package com.penmate.backend.application.auth.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Component
public class AuthSessionCache {

    private static final String ACCESS_PREFIX = "auth:access:";
    private static final String REFRESH_PREFIX = "auth:refresh:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public AuthSessionCache(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public void saveSession(AuthUserSessionPayload payload, AuthTokenBundle bundle) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(bundle, "bundle must not be null");
        Objects.requireNonNull(bundle.accessJti(), "accessJti must not be null");
        Objects.requireNonNull(bundle.refreshJti(), "refreshJti must not be null");
        Objects.requireNonNull(bundle.accessExpiresAt(), "accessExpiresAt must not be null");
        String json = toJson(payload);
        stringRedisTemplate.opsForValue().set(ACCESS_PREFIX + bundle.accessJti().trim(), json,
                Duration.between(java.time.Instant.now(), bundle.accessExpiresAt()));
        stringRedisTemplate.opsForValue().set(REFRESH_PREFIX + bundle.refreshJti().trim(), json,
                Duration.between(java.time.Instant.now(), bundle.refreshExpiresAt()));
    }

    public AuthUserSessionPayload getByAccessJti(String accessJti) {
        Objects.requireNonNull(accessJti, "accessJti must not be null");
        return parse(stringRedisTemplate.opsForValue().get(ACCESS_PREFIX + accessJti.trim()));
    }

    public AuthUserSessionPayload getByRefreshJti(String refreshJti) {
        Objects.requireNonNull(refreshJti, "refreshJti must not be null");
        return parse(stringRedisTemplate.opsForValue().get(REFRESH_PREFIX + refreshJti.trim()));
    }

    public void revokeAccess(String accessJti) {
        Objects.requireNonNull(accessJti, "accessJti must not be null");
        stringRedisTemplate.delete(ACCESS_PREFIX + accessJti.trim());
    }

    public void revokeRefresh(String refreshJti) {
        Objects.requireNonNull(refreshJti, "refreshJti must not be null");
        stringRedisTemplate.delete(REFRESH_PREFIX + refreshJti.trim());
    }

    public void updateSessionPayload(String accessJti, AuthUserSessionPayload payload) {
        updatePayload(ACCESS_PREFIX + accessJti.trim(), payload);
        if (payload.getRefreshJti() != null && !payload.getRefreshJti().isBlank()) {
            updatePayload(REFRESH_PREFIX + payload.getRefreshJti().trim(), payload);
        }
    }

    private void updatePayload(String key, AuthUserSessionPayload payload) {
        Long ttlSeconds = stringRedisTemplate.getExpire(key);
        if (ttlSeconds != null && ttlSeconds > 0) {
            stringRedisTemplate.opsForValue().set(key, toJson(payload), Duration.ofSeconds(ttlSeconds));
        }
    }

    private String toJson(AuthUserSessionPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw BusinessException.of("Failed to cache auth session");
        }
    }

    private AuthUserSessionPayload parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AuthUserSessionPayload.class);
        } catch (JsonProcessingException e) {
            throw BusinessException.of("Failed to parse auth session");
        }
    }
}

