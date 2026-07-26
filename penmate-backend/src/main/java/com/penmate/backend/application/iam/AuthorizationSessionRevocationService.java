package com.penmate.backend.application.iam;

import com.penmate.backend.application.auth.support.AuthSessionCache;
import com.penmate.backend.domain.auth.model.AuthSession;
import com.penmate.backend.domain.auth.repository.AuthSessionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuthorizationSessionRevocationService {
    private final AuthSessionRepository sessions;
    private final AuthSessionCache cache;
    private final CapabilityAuthorizationService authorization;

    public AuthorizationSessionRevocationService(AuthSessionRepository sessions,
                                                 AuthSessionCache cache,
                                                 CapabilityAuthorizationService authorization) {
        this.sessions = sessions;
        this.cache = cache;
        this.authorization = authorization;
    }

    public void revokeAll(List<Long> userIds) {
        for (Long userId : userIds) revokeAll(userId);
    }

    public void revokeAll(Long userId) {
        List<AuthSession> activeSessions = sessions.listByUser(userId);
        for (AuthSession session : activeSessions) {
            if (session.getCurrentAccessJti() != null) cache.revokeAccess(session.getCurrentAccessJti());
            if (session.getCurrentRefreshJtiHash() != null) {
                cache.revokeRefreshFingerprint(session.getCurrentRefreshJtiHash());
            }
        }
        sessions.revokeAll(userId, Instant.now());
        authorization.evict(userId);
    }
}
