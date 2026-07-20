package com.penmate.backend.infrastructure.ratelimit;

import com.penmate.backend.application.ratelimit.RateLimitStoreUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisTokenBucketRateLimitStoreTest {

    @Test
    void maps_atomic_script_result() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), eq(List.of("bucket")),
                anyString(), anyString(), anyString())).thenReturn(List.of(1L, 0L));

        var result = new RedisTokenBucketRateLimitStore(redis)
                .consume("bucket", 5, Duration.ofSeconds(10));

        assertThat(result.allowed()).isTrue();
        assertThat(result.retryAfterSeconds()).isZero();
    }

    @Test
    void converts_redis_outage_to_application_port_exception() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), eq(List.of("bucket")),
                anyString(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("offline"));

        assertThatThrownBy(() -> new RedisTokenBucketRateLimitStore(redis)
                .consume("bucket", 5, Duration.ofSeconds(10)))
                .isInstanceOf(RateLimitStoreUnavailableException.class);
    }
}
