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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountCredentialApplicationServiceTest {
    private final IamGateway iam = mock(IamGateway.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final AuthSessionCache cache = mock(AuthSessionCache.class);
    private AccountCredentialApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AccountCredentialApplicationService(iam, passwords, sessions, cache);
    }

    @Test
    void change_email_verifies_password_normalizes_email_and_revokes_every_session() {
        IamUser current = user(1001L, "old@example.com", "encoded");
        when(iam.findUserByUserId(1001L)).thenReturn(current);
        when(passwords.matches("secret", "encoded")).thenReturn(true);
        when(iam.findUserByEmail("new@example.com")).thenReturn(null);
        when(iam.updateEmail(1001L, "new@example.com")).thenReturn(1);
        when(sessions.listByUser(1001L)).thenReturn(List.of(
                session("access-a", "refresh-a"), session("access-b", "refresh-b")));

        service.changeEmail(1001L, "secret", "  NEW@Example.com ");

        verify(iam).updateEmail(1001L, "new@example.com");
        assertEverySessionWasRevoked();
    }

    @Test
    void change_email_rejects_a_duplicate_owned_by_another_user() {
        when(iam.findUserByUserId(1001L)).thenReturn(user(1001L, "old@example.com", "encoded"));
        when(passwords.matches("secret", "encoded")).thenReturn(true);
        when(iam.findUserByEmail("used@example.com"))
                .thenReturn(user(2002L, "used@example.com", "other-hash"));

        assertThatThrownBy(() -> service.changeEmail(1001L, "secret", "used@example.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email is already in use");

        verify(iam, never()).updateEmail(any(), anyString());
        verify(sessions, never()).revokeAll(any(), any());
    }

    @Test
    void credential_changes_reject_a_wrong_current_password_without_writes() {
        when(iam.findUserByUserId(1001L)).thenReturn(user(1001L, "old@example.com", "encoded"));
        when(passwords.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(1001L, "wrong", "new-password"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Current password is incorrect");

        verify(iam, never()).updatePassword(any(), anyString());
        verify(sessions, never()).revokeAll(any(), any());
    }

    @Test
    void change_password_persists_the_hash_and_revokes_every_session() {
        when(iam.findUserByUserId(1001L)).thenReturn(user(1001L, "old@example.com", "encoded"));
        when(passwords.matches("secret", "encoded")).thenReturn(true);
        when(passwords.encode("new-password")).thenReturn("new-hash");
        when(iam.updatePassword(1001L, "new-hash")).thenReturn(1);
        when(sessions.listByUser(1001L)).thenReturn(List.of(session("access-a", "refresh-a")));

        service.changePassword(1001L, "secret", "new-password");

        verify(iam).updatePassword(1001L, "new-hash");
        assertEverySessionWasRevoked();
    }

    private void assertEverySessionWasRevoked() {
        ArgumentCaptor<Instant> revokedAt = ArgumentCaptor.forClass(Instant.class);
        verify(sessions).revokeAll(eq(1001L), revokedAt.capture());
        assertThat(revokedAt.getValue()).isBeforeOrEqualTo(Instant.now());
        verify(cache).revokeAccess("access-a");
        verify(cache).revokeRefreshFingerprint("refresh-a");
    }

    private IamUser user(Long userId, String email, String passwordHash) {
        IamUser user = new IamUser();
        user.setId(userId);
        user.setUserId(userId);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        return user;
    }

    private AuthSession session(String accessJti, String refreshJti) {
        AuthSession session = new AuthSession();
        session.setCurrentAccessJti(accessJti);
        session.setCurrentRefreshJtiHash(refreshJti);
        return session;
    }
}
