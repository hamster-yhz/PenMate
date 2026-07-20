package com.penmate.backend.infrastructure.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacRateLimitKeyEncoderTest {

    private final HmacRateLimitKeyEncoder encoder = new HmacRateLimitKeyEncoder("independent-test-secret");

    @Test
    void hashes_sensitive_subjects_deterministically() {
        String first = encoder.encode("login-email", "person@example.com", true);
        String second = encoder.encode("login-email", "person@example.com", true);

        assertThat(first).isEqualTo(second).startsWith("rate-limit:login-email:")
                .doesNotContain("person@example.com");
    }

    @Test
    void keeps_non_sensitive_user_id_readable() {
        assertThat(encoder.encode("password-change", "123", false))
                .isEqualTo("rate-limit:password-change:123");
    }
}
