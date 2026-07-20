package com.penmate.backend.infrastructure.ratelimit;

import com.penmate.backend.application.ratelimit.RateLimitStoreUnavailableException;
import com.penmate.backend.application.ratelimit.port.RateLimitBucketStore;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RedisTokenBucketRateLimitStore implements RateLimitBucketStore {
    private static final DefaultRedisScript<List> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local capacity = tonumber(ARGV[1])
            local refill_ms = tonumber(ARGV[2])
            local ttl_ms = tonumber(ARGV[3])
            local time = redis.call('TIME')
            local now_ms = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'updated_at')
            local tokens = tonumber(state[1]) or capacity
            local updated_at = tonumber(state[2]) or now_ms
            local elapsed = math.max(0, now_ms - updated_at)
            tokens = math.min(capacity, tokens + elapsed / refill_ms)
            local allowed = 0
            local retry_after = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            else
              retry_after = math.max(1, math.ceil((1 - tokens) * refill_ms / 1000))
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'updated_at', tostring(now_ms))
            redis.call('PEXPIRE', KEYS[1], ttl_ms)
            return {allowed, retry_after}
            """, List.class);

    private final StringRedisTemplate redis;

    public RedisTokenBucketRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Consumption consume(String key, int capacity, Duration refillPeriod) {
        long refillMillis = refillPeriod.toMillis();
        long ttlMillis = Math.max(60_000L, Math.multiplyExact(Math.multiplyExact(refillMillis, capacity), 2L));
        try {
            List<?> result = redis.execute(CONSUME_SCRIPT, List.of(key), String.valueOf(capacity),
                    String.valueOf(refillMillis), String.valueOf(ttlMillis));
            if (result == null || result.size() != 2) {
                throw new RateLimitStoreUnavailableException("Redis returned an invalid token bucket result", null);
            }
            return new Consumption(number(result.get(0)) == 1L, number(result.get(1)));
        } catch (DataAccessException exception) {
            throw new RateLimitStoreUnavailableException("Redis rate limit store is unavailable", exception);
        } catch (RateLimitStoreUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RateLimitStoreUnavailableException("Redis token bucket execution failed", exception);
        }
    }

    @Override
    public void clear(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException exception) {
            throw new RateLimitStoreUnavailableException("Redis rate limit store is unavailable", exception);
        } catch (RuntimeException exception) {
            throw new RateLimitStoreUnavailableException("Redis rate limit store is unavailable", exception);
        }
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new RateLimitStoreUnavailableException("Redis returned a non-numeric token bucket result", exception);
        }
    }
}
