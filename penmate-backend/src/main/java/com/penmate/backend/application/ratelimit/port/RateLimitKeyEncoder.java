package com.penmate.backend.application.ratelimit.port;

public interface RateLimitKeyEncoder {
    String encode(String namespace, String subject, boolean hashSubject);
}
