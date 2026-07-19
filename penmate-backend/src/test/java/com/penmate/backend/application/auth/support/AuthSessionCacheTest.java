package com.penmate.backend.application.auth.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionCacheTest {

    @Test
    void should_reject_null_redis_template_in_constructor() {
        ObjectMapper objectMapper = new ObjectMapper();

        assertThatThrownBy(() -> new AuthSessionCache(null, objectMapper))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("stringRedisTemplate");
    }

    @Test
    void should_trim_access_jti_before_revoke_access() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthSessionCache cache = new AuthSessionCache(redisTemplate, objectMapper);

        cache.revokeAccess("  access-jti-1  ");

        verify(redisTemplate).delete("auth:access:access-jti-1");
    }

    @Test
    void should_trim_refresh_jti_before_revoke_refresh() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthSessionCache cache = new AuthSessionCache(redisTemplate, objectMapper);

        cache.revokeRefresh("  refresh-jti-9  ");

        verify(redisTemplate).delete("auth:refresh:refresh-jti-9");
    }

    @Test
    void should_reject_null_access_jti_when_revoking_access() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthSessionCache cache = new AuthSessionCache(redisTemplate, objectMapper);

        assertThatThrownBy(() -> cache.revokeAccess(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("accessJti")
                .hasMessageContaining("must not be null");
    }

    @Test
    void should_reject_null_refresh_jti_when_revoking_refresh() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthSessionCache cache = new AuthSessionCache(redisTemplate, objectMapper);

        assertThatThrownBy(() -> cache.revokeRefresh(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("refreshJti")
                .hasMessageContaining("must not be null");
    }

    @Test
    void should_trim_access_jti_before_getting_session_from_cache() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthSessionCache cache = new AuthSessionCache(redisTemplate, objectMapper);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:access:access-jti-2")).thenReturn(null);

        cache.getByAccessJti("  access-jti-2  ");

        verify(valueOperations).get("auth:access:access-jti-2");
    }

    @Test
    void should_reject_null_access_jti_when_getting_session() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthSessionCache cache = new AuthSessionCache(redisTemplate, objectMapper);

        assertThatThrownBy(() -> cache.getByAccessJti(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("accessJti")
                .hasMessageContaining("must not be null");
    }

    @Test
    void should_trim_refresh_jti_before_getting_session_from_cache() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthSessionCache cache = new AuthSessionCache(redisTemplate, objectMapper);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:refresh:refresh-jti-3")).thenReturn(null);

        cache.getByRefreshJti("  refresh-jti-3  ");

        verify(valueOperations).get("auth:refresh:refresh-jti-3");
    }

    @Test
    void should_reject_null_refresh_jti_when_getting_session() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthSessionCache cache = new AuthSessionCache(redisTemplate, objectMapper);

        assertThatThrownBy(() -> cache.getByRefreshJti(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("refreshJti")
                .hasMessageContaining("must not be null");
    }

    @Test
    void should_trim_access_jti_before_saving_session_to_cache() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthSessionCache cache = new AuthSessionCache(redisTemplate, objectMapper);

        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(101L);

        AuthTokenBundle bundle = new AuthTokenBundle(
                "access-token",
                "refresh-token",
                "  access-jti-save  ",
                "refresh-jti-save",
                Instant.now().plus(5, java.time.temporal.ChronoUnit.MINUTES),
                Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS)
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cache.saveSession(payload, bundle);

        verify(valueOperations).set(eq("auth:access:access-jti-save"), anyString(), any(Duration.class));
    }

    @Test
    void should_reject_null_bundle_when_saving_session() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthSessionCache cache = new AuthSessionCache(redisTemplate, objectMapper);

        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(103L);

        assertThatThrownBy(() -> cache.saveSession(payload, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bundle")
                .hasMessageContaining("must not be null");
    }

    @Test
    void should_reject_null_access_expires_at_when_saving_session() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthSessionCache cache = new AuthSessionCache(redisTemplate, objectMapper);

        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(104L);

        AuthTokenBundle bundle = new AuthTokenBundle(
                "access-token",
                "refresh-token",
                "access-jti-save-3",
                "refresh-jti-save-3",
                null,
                Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS)
        );

        assertThatThrownBy(() -> cache.saveSession(payload, bundle))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("accessExpiresAt")
                .hasMessageContaining("must not be null");
    }

    @Test
    void should_trim_refresh_jti_before_saving_session_to_cache() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthSessionCache cache = new AuthSessionCache(redisTemplate, objectMapper);

        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(102L);

        AuthTokenBundle bundle = new AuthTokenBundle(
                "access-token",
                "refresh-token",
                "access-jti-save-2",
                "  refresh-jti-save-2  ",
                Instant.now().plus(5, java.time.temporal.ChronoUnit.MINUTES),
                Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS)
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cache.saveSession(payload, bundle);

        verify(valueOperations).set(eq("auth:refresh:refresh-jti-save-2"), anyString(), any(Duration.class));
    }
}

