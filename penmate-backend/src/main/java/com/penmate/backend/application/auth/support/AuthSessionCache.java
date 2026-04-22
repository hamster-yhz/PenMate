package com.penmate.backend.application.auth.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthSessionCache {

    private static final String ACCESS_PREFIX = "auth:access:";
    private static final String REFRESH_PREFIX = "auth:refresh:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public AuthSessionCache(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public void saveSession(AuthUserSessionPayload payload, AuthTokenBundle bundle) {
        String json = toJson(payload);
        stringRedisTemplate.opsForValue().set(ACCESS_PREFIX + bundle.accessJti(), json,
                Duration.between(java.time.LocalDateTime.now(), bundle.accessExpiresAt()));
        stringRedisTemplate.opsForValue().set(REFRESH_PREFIX + bundle.refreshJti(), json,
                Duration.between(java.time.LocalDateTime.now(), bundle.refreshExpiresAt()));
    }

    public AuthUserSessionPayload getByAccessJti(String accessJti) {
        return parse(stringRedisTemplate.opsForValue().get(ACCESS_PREFIX + accessJti));
    }

    public AuthUserSessionPayload getByRefreshJti(String refreshJti) {
        return parse(stringRedisTemplate.opsForValue().get(REFRESH_PREFIX + refreshJti));
    }

    public void revokeAccess(String accessJti) {
        stringRedisTemplate.delete(ACCESS_PREFIX + accessJti);
    }

    public void revokeRefresh(String refreshJti) {
        stringRedisTemplate.delete(REFRESH_PREFIX + refreshJti);
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

