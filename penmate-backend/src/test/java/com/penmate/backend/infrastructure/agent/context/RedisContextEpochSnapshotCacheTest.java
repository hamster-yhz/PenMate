package com.penmate.backend.infrastructure.agent.context;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisContextEpochSnapshotCacheTest {

    @Test
    void uses_a_short_lived_versioned_snapshot_key() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("agent:context-epoch:9001")).thenReturn("snapshot");
        RedisContextEpochSnapshotCache cache = new RedisContextEpochSnapshotCache(redis);

        cache.put(9001L, "snapshot");

        verify(values).set("agent:context-epoch:9001", "snapshot", Duration.ofMinutes(30));
        assertThat(cache.get(9001L)).isEqualTo("snapshot");
    }
}
