package com.penmate.backend.application.ratelimit.port;

import java.time.Duration;

public interface RateLimitBucketStore {
    Consumption consume(String key, int capacity, Duration refillPeriod);
    void clear(String key);

    record Consumption(boolean allowed, long retryAfterSeconds) {
    }
}
