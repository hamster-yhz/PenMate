package com.penmate.backend.application.iam;

import com.penmate.backend.application.auth.support.AuthSessionCache;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.auth.model.AuthSession;
import com.penmate.backend.domain.auth.repository.AuthSessionRepository;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class AccountDeletionApplicationService {
    public static final Duration WAITING_PERIOD = Duration.ofDays(30);

    private final IamGateway iam;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionRepository sessions;
    private final AuthSessionCache sessionCache;
    private final AccountPurgeService purgeService;
    private final AdminSafetyPolicy adminSafetyPolicy;

    public AccountDeletionApplicationService(IamGateway iam, PasswordEncoder passwordEncoder,
                                             AuthSessionRepository sessions, AuthSessionCache sessionCache,
                                             AccountPurgeService purgeService,
                                             AdminSafetyPolicy adminSafetyPolicy) {
        this.iam = iam;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.sessionCache = sessionCache;
        this.purgeService = purgeService;
        this.adminSafetyPolicy = adminSafetyPolicy;
    }

    @Transactional
    public DeletionReceipt requestDeletion(Long userId, String currentPassword, boolean confirmed) {
        if (!confirmed) throw BusinessException.badRequest("Account deletion must be explicitly confirmed");
        IamUser user = iam.findUserByUserId(userId);
        if (user == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw BusinessException.badRequest("Current password is incorrect");
        }
        adminSafetyPolicy.requireAccountMutationAllowed(userId, userId);
        List<AuthSession> activeSessions = sessions.listByUser(userId);
        Instant requestedAt = Instant.now();
        Instant dueAt = requestedAt.plus(WAITING_PERIOD);
        if (iam.requestUserDeletion(userId, requestedAt, dueAt) != 1) {
            throw BusinessException.conflict("Account deletion is already pending");
        }
        sessions.revokeAll(userId, requestedAt);
        for (AuthSession session : activeSessions) {
            sessionCache.revokeAccess(session.getCurrentAccessJti());
            sessionCache.revokeRefreshFingerprint(session.getCurrentRefreshJtiHash());
        }
        return new DeletionReceipt(requestedAt, dueAt);
    }

    @Transactional
    public IamUser restore(Long userId) {
        if (iam.restorePendingUserDeletion(userId) != 1) {
            throw BusinessException.notFound("Pending account deletion not found");
        }
        IamUser restored = iam.findUserByUserId(userId);
        if (restored == null) throw BusinessException.of("Failed to restore account");
        return restored;
    }

    public int purgeExpiredAccounts() {
        Instant now = Instant.now();
        int purged = 0;
        for (Long userId : iam.findDeletionDueUserIds(now)) {
            try {
                purgeService.purge(userId, now);
                purged++;
            } catch (RuntimeException exception) {
                log.error("Failed to purge expired account: userId={}", userId, exception);
            }
        }
        if (purged > 0) log.info("Purged expired accounts: count={}", purged);
        return purged;
    }

    public record DeletionReceipt(Instant requestedAt, Instant deletionDueAt) { }
}
