package com.penmate.backend.application.ratelimit;

public class RateLimitStoreUnavailableException extends RuntimeException {
    public RateLimitStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
