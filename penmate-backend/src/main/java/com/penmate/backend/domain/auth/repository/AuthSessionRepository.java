package com.penmate.backend.domain.auth.repository;

import com.penmate.backend.domain.auth.model.AuthSession;

import java.time.Instant;
import java.util.List;

public interface AuthSessionRepository {
    int insert(AuthSession session);

    AuthSession findByIdAndUser(String sessionId, Long userId);

    List<AuthSession> listByUser(Long userId);

    int rotate(String sessionId, Long userId, String expectedRefreshJtiHash, String accessJti,
               String refreshJtiHash, String ipAddress, Instant refreshExpiresAt, Instant lastSeenAt);

    int revoke(String sessionId, Long userId, Instant revokedAt);

    int revokeAll(Long userId, Instant revokedAt);

    List<AuthSession> revokeAllExcept(Long userId, String retainedSessionId, Instant revokedAt);
}
