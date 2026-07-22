package com.penmate.backend.domain.auth.model;

import lombok.Data;

import java.time.Instant;

@Data
public class AuthSession {
    private Long id;
    private String sessionId;
    private Long userId;
    private String currentAccessJti;
    private String currentRefreshJtiHash;
    private String deviceName;
    private String browserName;
    private String operatingSystem;
    private String userAgent;
    private String ipAddress;
    private Instant createdAt;
    private Instant lastSeenAt;
    private Instant refreshExpiresAt;
    private Instant revokedAt;
}
