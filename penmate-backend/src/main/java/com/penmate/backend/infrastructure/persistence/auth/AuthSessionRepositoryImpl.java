package com.penmate.backend.infrastructure.persistence.auth;

import com.penmate.backend.domain.auth.model.AuthSession;
import com.penmate.backend.domain.auth.repository.AuthSessionRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class AuthSessionRepositoryImpl implements AuthSessionRepository {
    private final AuthSessionMapper mapper;

    public AuthSessionRepositoryImpl(AuthSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override public int insert(AuthSession session) { return mapper.insert(session); }
    @Override public AuthSession findByIdAndUser(String sessionId, Long userId) { return mapper.findByIdAndUser(sessionId, userId); }
    @Override public List<AuthSession> listByUser(Long userId) { return mapper.listByUser(userId); }
    @Override public int rotate(String sessionId, Long userId, String expectedRefreshJtiHash, String accessJti,
                                String refreshJtiHash, String ipAddress, Instant refreshExpiresAt, Instant lastSeenAt) {
        return mapper.rotate(sessionId, userId, expectedRefreshJtiHash, accessJti, refreshJtiHash, ipAddress,
                refreshExpiresAt, lastSeenAt);
    }
    @Override public int revoke(String sessionId, Long userId, Instant revokedAt) {
        return mapper.revoke(sessionId, userId, revokedAt);
    }
    @Override public int revokeAll(Long userId, Instant revokedAt) { return mapper.revokeAll(userId, revokedAt); }
    @Override public List<AuthSession> revokeAllExcept(Long userId, String retainedSessionId, Instant revokedAt) {
        return mapper.revokeAllExcept(userId, retainedSessionId, revokedAt);
    }
}
