package com.penmate.backend.application.ratelimit;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.ratelimit.port.RateLimitBucketStore;
import com.penmate.backend.application.ratelimit.port.RateLimitKeyEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class RateLimitApplicationServiceTest {

    private final RateLimitBucketStore store = mock(RateLimitBucketStore.class);
    private final RateLimitKeyEncoder encoder = (namespace, subject, hashed) -> namespace + ":" + subject;
    private final RateLimitApplicationService service = new RateLimitApplicationService(store, encoder);

    @Test
    void rejects_exhausted_bucket_with_retry_after() {
        when(store.consume("login-email:person@example.com", 5, RateLimitAction.LOGIN_EMAIL.refillPeriod()))
                .thenReturn(new RateLimitBucketStore.Consumption(false, 73));

        assertThatThrownBy(() -> service.consume(RateLimitAction.LOGIN_EMAIL, "person@example.com"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
                    assertThat(exception.getHttpStatus().value()).isEqualTo(429);
                    assertThat(exception.getDetails()).isEqualTo(java.util.Map.of("retryAfterSeconds", 73L));
                });
    }

    @Test
    void fails_closed_when_store_is_unavailable() {
        when(store.consume("refresh-token:token", 10, RateLimitAction.REFRESH_TOKEN.refillPeriod()))
                .thenThrow(new RateLimitStoreUnavailableException("offline", null));

        assertThatThrownBy(() -> service.consume(RateLimitAction.REFRESH_TOKEN, "token"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("RATE_LIMIT_SERVICE_UNAVAILABLE");
                    assertThat(exception.getHttpStatus().value()).isEqualTo(503);
                });
    }

    @Test
    void consumes_every_related_bucket_before_rejecting() {
        when(store.consume("login-email:person@example.com", 5, RateLimitAction.LOGIN_EMAIL.refillPeriod()))
                .thenReturn(new RateLimitBucketStore.Consumption(false, 1));
        when(store.consume("login-ip:203.0.113.1", 20, RateLimitAction.LOGIN_IP.refillPeriod()))
                .thenReturn(new RateLimitBucketStore.Consumption(true, 0));

        assertThatThrownBy(() -> service.consumeAll(
                new RateLimitApplicationService.Limit(RateLimitAction.LOGIN_EMAIL, "person@example.com"),
                new RateLimitApplicationService.Limit(RateLimitAction.LOGIN_IP, "203.0.113.1")))
                .isInstanceOf(BusinessException.class);

        verify(store).consume("login-ip:203.0.113.1", 20, RateLimitAction.LOGIN_IP.refillPeriod());
    }
}
