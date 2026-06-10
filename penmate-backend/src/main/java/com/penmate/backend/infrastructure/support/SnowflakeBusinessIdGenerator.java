package com.penmate.backend.infrastructure.support;

import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Business ID generator backed by a 53-bit Snowflake layout.
 * <p>The generated value stays within JavaScript's safe integer range.</p>
 */
@Component
public class SnowflakeBusinessIdGenerator implements BusinessIdGenerator {

    private static final long CUSTOM_EPOCH_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    private static final long WORKER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 7L;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    private static final long MAX_ID = (1L << 53) - 1;

    private final long workerId;
    private final Clock clock;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeBusinessIdGenerator() {
        this(resolveWorkerId(), Clock.systemUTC());
    }

    SnowflakeBusinessIdGenerator(long workerId, Clock clock) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public synchronized Long nextId() {
        long timestamp = currentTimestamp();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards while generating business ID");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0L) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        long id = ((timestamp - CUSTOM_EPOCH_MILLIS) << (WORKER_ID_BITS + SEQUENCE_BITS))
                | (workerId << SEQUENCE_BITS)
                | sequence;
        if (id <= 0 || id > MAX_ID) {
            throw new IllegalStateException("Generated business ID is outside JavaScript safe integer range");
        }
        return id;
    }

    private long currentTimestamp() {
        long timestamp = clock.millis();
        if (timestamp < CUSTOM_EPOCH_MILLIS) {
            throw new IllegalStateException("System clock is earlier than business ID epoch");
        }
        return timestamp;
    }

    private long waitUntilNextMillis(long previousTimestamp) {
        long timestamp = currentTimestamp();
        while (timestamp <= previousTimestamp) {
            Thread.onSpinWait();
            timestamp = currentTimestamp();
        }
        return timestamp;
    }

    private static long resolveWorkerId() {
        String value = System.getProperty(
                "penmate.business-id.worker-id",
                System.getenv().getOrDefault("PENMATE_BUSINESS_ID_WORKER_ID", "0")
        );
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("workerId must be a number", ex);
        }
    }
}
