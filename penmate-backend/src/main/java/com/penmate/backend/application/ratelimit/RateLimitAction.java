package com.penmate.backend.application.ratelimit;

import java.time.Duration;

public enum RateLimitAction {
    LOGIN_EMAIL("login-email", 5, Duration.ofMinutes(3), true),
    LOGIN_IP("login-ip", 20, Duration.ofSeconds(15), true),
    REFRESH_TOKEN("refresh-token", 10, Duration.ofSeconds(6), true),
    REFRESH_IP("refresh-ip", 60, Duration.ofSeconds(1), true),
    PASSWORD_CHANGE("password-change", 5, Duration.ofMinutes(12), false),
    EMBEDDING_DIMENSION_PROBE("embedding-dimension-probe", 10, Duration.ofSeconds(6), false),
    MODEL_CONNECTION_TEST("model-connection-test", 10, Duration.ofSeconds(6), false);

    private final String namespace;
    private final int capacity;
    private final Duration refillPeriod;
    private final boolean hashSubject;

    RateLimitAction(String namespace, int capacity, Duration refillPeriod, boolean hashSubject) {
        this.namespace = namespace;
        this.capacity = capacity;
        this.refillPeriod = refillPeriod;
        this.hashSubject = hashSubject;
    }

    public String namespace() { return namespace; }
    public int capacity() { return capacity; }
    public Duration refillPeriod() { return refillPeriod; }
    public boolean hashSubject() { return hashSubject; }
}
