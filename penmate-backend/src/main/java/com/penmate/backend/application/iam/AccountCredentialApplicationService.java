package com.penmate.backend.application.iam;

import com.penmate.backend.application.auth.support.AuthSessionCache;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.auth.model.AuthSession;
import com.penmate.backend.domain.auth.repository.AuthSessionRepository;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class AccountCredentialApplicationService {
    private final IamGateway iam;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionRepository sessions;
    private final AuthSessionCache sessionCache;

    public AccountCredentialApplicationService(IamGateway iam, PasswordEncoder passwordEncoder,
                                               AuthSessionRepository sessions, AuthSessionCache sessionCache) {
        this.iam = iam;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.sessionCache = sessionCache;
    }

    @Transactional
    public void changeEmail(Long userId, String currentPassword, String newEmail) {
        IamUser user = requirePassword(userId, currentPassword);
        String normalizedEmail = newEmail.trim().toLowerCase(Locale.ROOT);
        IamUser existing = iam.findUserByEmail(normalizedEmail);
        if (existing != null && !sameUser(existing, userId)) {
            throw BusinessException.conflict("Email is already in use");
        }
        if (Objects.equals(user.getEmail(), normalizedEmail)) {
            throw BusinessException.conflict("New email must be different from the current email");
        }
        if (iam.updateEmail(userId, normalizedEmail) != 1) {
            throw BusinessException.of("Email update failed");
        }
        revokeAllSessions(userId);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        requirePassword(userId, currentPassword);
        if (iam.updatePassword(userId, passwordEncoder.encode(newPassword)) != 1) {
            throw BusinessException.of("Password update failed");
        }
        revokeAllSessions(userId);
    }

    private IamUser requirePassword(Long userId, String currentPassword) {
        IamUser user = iam.findUserByUserId(userId);
        if (user == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw BusinessException.badRequest("Current password is incorrect");
        }
        return user;
    }

    private boolean sameUser(IamUser user, Long userId) {
        return Objects.equals(user.getUserId(), userId) || Objects.equals(user.getId(), userId);
    }

    private void revokeAllSessions(Long userId) {
        List<AuthSession> activeSessions = sessions.listByUser(userId);
        Instant revokedAt = Instant.now();
        sessions.revokeAll(userId, revokedAt);
        for (AuthSession session : activeSessions) {
            if (session.getCurrentAccessJti() != null) sessionCache.revokeAccess(session.getCurrentAccessJti());
            if (session.getCurrentRefreshJtiHash() != null) {
                sessionCache.revokeRefreshFingerprint(session.getCurrentRefreshJtiHash());
            }
        }
    }
}
