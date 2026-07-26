package com.penmate.backend.application.iam;

import com.penmate.backend.application.auth.support.AuthSessionCache;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.auth.model.AuthSession;
import com.penmate.backend.domain.auth.repository.AuthSessionRepository;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionApplicationServiceTest {
    private final IamGateway iam = mock(IamGateway.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final AuthSessionCache cache = mock(AuthSessionCache.class);
    private final AccountPurgeService purge = mock(AccountPurgeService.class);
    private final AdminSafetyPolicy adminSafety = mock(AdminSafetyPolicy.class);
    private AccountDeletionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionApplicationService(iam, passwords, sessions, cache, purge, adminSafety);
    }

    @Test
    void requests_a_thirty_day_deletion_and_revokes_every_active_session() {
        IamUser user = new IamUser();
        user.setUserId(1001L);
        user.setPasswordHash("encoded");
        AuthSession laptop = session("laptop", "access-a", "refresh-a");
        AuthSession phone = session("phone", "access-b", "refresh-b");
        when(iam.findUserByUserId(1001L)).thenReturn(user);
        when(passwords.matches("secret", "encoded")).thenReturn(true);
        when(sessions.listByUser(1001L)).thenReturn(List.of(laptop, phone));
        when(iam.requestUserDeletion(eq(1001L), any(), any())).thenReturn(1);

        AccountDeletionApplicationService.DeletionReceipt receipt =
                service.requestDeletion(1001L, "secret", true);

        ArgumentCaptor<Instant> requested = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> due = ArgumentCaptor.forClass(Instant.class);
        verify(iam).requestUserDeletion(eq(1001L), requested.capture(), due.capture());
        assertThat(Duration.between(requested.getValue(), due.getValue())).isEqualTo(Duration.ofDays(30));
        assertThat(receipt.deletionDueAt()).isEqualTo(due.getValue());
        verify(sessions).revokeAll(eq(1001L), eq(requested.getValue()));
        verify(cache).revokeAccess("access-a");
        verify(cache).revokeRefreshFingerprint("refresh-a");
        verify(cache).revokeAccess("access-b");
        verify(cache).revokeRefreshFingerprint("refresh-b");
    }

    @Test
    void rejects_a_wrong_password_without_changing_account_state() {
        IamUser user = new IamUser();
        user.setUserId(1001L);
        user.setPasswordHash("encoded");
        when(iam.findUserByUserId(1001L)).thenReturn(user);
        when(passwords.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> service.requestDeletion(1001L, "wrong", true))
                .isInstanceOf(BusinessException.class);

        verify(iam, never()).requestUserDeletion(any(), any(), any());
        verify(sessions, never()).revokeAll(any(), any());
    }

    private AuthSession session(String sessionId, String accessJti, String refreshJti) {
        AuthSession session = new AuthSession();
        session.setSessionId(sessionId);
        session.setCurrentAccessJti(accessJti);
        session.setCurrentRefreshJtiHash(refreshJti);
        return session;
    }
}
