package com.penmate.backend.infrastructure.agent.run;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAgentCheckpointCacheTest {

    @Test
    void maps_application_cache_operations_to_the_versioned_redis_key() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("agent:checkpoint:v2:70001:latest")).thenReturn("state-json");
        RedisAgentCheckpointCache cache = new RedisAgentCheckpointCache(redis);

        cache.put(70001L, "state-json", Duration.ofMinutes(30));
        assertThat(cache.get(70001L)).isEqualTo("state-json");
        cache.delete(70001L);

        verify(values).set("agent:checkpoint:v2:70001:latest", "state-json", Duration.ofMinutes(30));
        verify(redis).delete("agent:checkpoint:v2:70001:latest");
    }
}
