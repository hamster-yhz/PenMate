package com.penmate.backend.infrastructure.support;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeBusinessIdGeneratorTest {

    private static final long JAVASCRIPT_MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    @Test
    void generatedIdFitsInJavascriptSafeIntegerRange() {
        SnowflakeBusinessIdGenerator generator = new SnowflakeBusinessIdGenerator(31L, Clock.fixed(
                Instant.parse("2090-12-31T23:59:59.999Z"),
                ZoneOffset.UTC
        ));

        Long id = generator.nextId();

        assertThat(id).isPositive();
        assertThat(id).isLessThanOrEqualTo(JAVASCRIPT_MAX_SAFE_INTEGER);
        assertThat(String.valueOf(id)).hasSizeLessThanOrEqualTo(16);
    }

    @Test
    void generatedIdsAreMonotonicAndUniqueWithinTheSameMillisecond() {
        SnowflakeBusinessIdGenerator generator = new SnowflakeBusinessIdGenerator(7L, Clock.fixed(
                Instant.parse("2026-06-10T00:00:00.123Z"),
                ZoneOffset.UTC
        ));

        Set<Long> ids = new HashSet<>();
        long previous = generator.nextId();
        ids.add(previous);

        for (int i = 0; i < 127; i++) {
            long next = generator.nextId();
            assertThat(next).isGreaterThan(previous);
            assertThat(ids.add(next)).isTrue();
            previous = next;
        }
    }

    @Test
    void rejectsWorkerIdOutsideConfiguredBitRange() {
        assertThatThrownBy(() -> new SnowflakeBusinessIdGenerator(32L, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workerId");

        assertThatThrownBy(() -> new SnowflakeBusinessIdGenerator(-1L, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workerId");
    }

    @Test
    void rejectsTimestampsOutsideJavascriptSafeIntegerRange() {
        SnowflakeBusinessIdGenerator generator = new SnowflakeBusinessIdGenerator(31L, Clock.fixed(
                Instant.parse("2095-12-31T23:59:59.999Z"),
                ZoneOffset.UTC
        ));

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JavaScript safe integer");
    }
}
