package com.penmate.backend.application.ratelimit;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.ratelimit.port.RateLimitBucketStore;
import com.penmate.backend.application.ratelimit.port.RateLimitKeyEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Service
public class RateLimitApplicationService {
    private final RateLimitBucketStore store;
    private final RateLimitKeyEncoder keyEncoder;

    public RateLimitApplicationService(RateLimitBucketStore store, RateLimitKeyEncoder keyEncoder) {
        this.store = store;
        this.keyEncoder = keyEncoder;
    }

    public void consume(RateLimitAction action, String subject) {
        Objects.requireNonNull(action, "action must not be null");
        String normalizedSubject = requireSubject(subject);
        try {
            RateLimitBucketStore.Consumption result = store.consume(key(action, normalizedSubject),
                    action.capacity(), action.refillPeriod());
            if (!result.allowed()) {
                long retryAfter = Math.max(1, result.retryAfterSeconds());
                throw BusinessException.of(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                        "Too many requests", Map.of("retryAfterSeconds", retryAfter));
            }
        } catch (RateLimitStoreUnavailableException exception) {
            throw BusinessException.of(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_SERVICE_UNAVAILABLE",
                    "Rate limit service is unavailable", null);
        }
    }

    public void consumeAll(Limit... limits) {
        BusinessException failure = null;
        for (Limit limit : limits) {
            try {
                consume(limit.action(), limit.subject());
            } catch (BusinessException exception) {
                if (failure == null || exception.getHttpStatus() == HttpStatus.SERVICE_UNAVAILABLE) {
                    failure = exception;
                }
            }
        }
        if (failure != null) throw failure;
    }

    public void clear(RateLimitAction action, String subject) {
        Objects.requireNonNull(action, "action must not be null");
        try {
            store.clear(key(action, requireSubject(subject)));
        } catch (RateLimitStoreUnavailableException exception) {
            throw BusinessException.of(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_SERVICE_UNAVAILABLE",
                    "Rate limit service is unavailable", null);
        }
    }

    private String key(RateLimitAction action, String subject) {
        return keyEncoder.encode(action.namespace(), subject, action.hashSubject());
    }

    private String requireSubject(String subject) {
        if (subject == null || subject.isBlank()) throw BusinessException.badRequest("Rate limit subject is required");
        return subject.trim();
    }

    public record Limit(RateLimitAction action, String subject) {
    }
}
