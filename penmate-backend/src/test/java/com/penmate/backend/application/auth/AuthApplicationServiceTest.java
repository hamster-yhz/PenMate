package com.penmate.backend.application.auth;

import com.penmate.backend.application.auth.command.LoginCommand;
import com.penmate.backend.application.auth.command.RefreshCommand;
import com.penmate.backend.application.auth.support.AuthSessionCache;
import com.penmate.backend.application.auth.support.AuthTokenBundle;
import com.penmate.backend.application.auth.support.AuthTokenFingerprint;
import com.penmate.backend.application.auth.support.AuthTokenService;
import com.penmate.backend.application.auth.support.AuthUserSessionPayload;
import com.penmate.backend.application.auth.support.ParsedToken;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import com.penmate.backend.domain.auth.repository.AuthSessionRepository;
import com.penmate.backend.domain.auth.repository.UserUiPreferencesRepository;
import com.penmate.backend.domain.auth.model.UserUiPreferences;
import com.penmate.backend.domain.auth.model.AuthSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private IamGateway iamGateway;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private AuthSessionCache authSessionCache;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthSessionRepository authSessions;

    @Mock
    private UserUiPreferencesRepository uiPreferences;

    @InjectMocks
    private AuthApplicationService authApplicationService;

    @Test
    void UT_APP_AUTH_LOGIN_SUCCESS() {
        String traceId = "UT-TRACE-AUTH-LOGIN-SUCCESS";
        IamUser user = new IamUser();
        user.setId(1001L);
        user.setUserId(1001L);
        user.setEmail("author@penmate.ai");
        user.setStatus(1);
        user.setAuthorizationVersion(4L);
        user.setPasswordHash("StrongPass!23");
        when(iamGateway.findUserByEmail("author@penmate.ai")).thenReturn(user);
        when(passwordEncoder.matches("StrongPass!23", "StrongPass!23")).thenReturn(true);
        when(iamGateway.findRolesByUserId(1001L)).thenReturn(List.of());
        when(iamGateway.findPermissionsByUserId(1001L)).thenReturn(List.of());
        when(authTokenService.issueTokens(any(AuthUserSessionPayload.class))).thenReturn(new AuthTokenBundle(
                "atk_1", "rtk_1", "ajti_1", "rjti_1", java.time.Instant.now().plusSeconds(1800),
                java.time.Instant.now().plusSeconds(604800)
        ));
        when(authSessions.insert(any())).thenReturn(1);

        Map<String, Object> result = authApplicationService.login(new LoginCommand("author@penmate.ai", "StrongPass!23"), traceId);

        ArgumentCaptor<AuthUserSessionPayload> payloadCaptor = ArgumentCaptor.forClass(AuthUserSessionPayload.class);
        verify(authSessionCache).saveSession(payloadCaptor.capture(), any(AuthTokenBundle.class));
        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessions).insert(sessionCaptor.capture());
        verify(iamGateway).touchLastLoginByUserId(1001L);
        verify(iamGateway).findRolesByUserId(1001L);
        verify(iamGateway).findPermissionsByUserId(1001L);
        assertThat(payloadCaptor.getValue().getUserId()).isEqualTo(1001L);
        assertThat(payloadCaptor.getValue().getEmail()).isEqualTo("author@penmate.ai");
        assertThat(sessionCaptor.getValue().getCurrentRefreshJtiHash())
                .isEqualTo(AuthTokenFingerprint.sha256("rjti_1"))
                .isNotEqualTo("rjti_1");
        assertThat(result).containsKeys("accessToken", "refreshToken");
    }

    @Test
    void UT_APP_AUTH_LOGIN_INVALID_CREDENTIALS() {
        when(iamGateway.findUserByEmail("x@x.com")).thenReturn(null);

        assertThatThrownBy(() -> authApplicationService.login(new LoginCommand("x@x.com", "bad"), "trace"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void UT_APP_AUTH_LOGOUT_SUCCESS() {
        ParsedToken parsedToken = new ParsedToken(1001L, "ajti_1", "ACCESS");
        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(1001L);
        payload.setRefreshJti("rjti_1");
        when(authTokenService.parseAccessToken("atk_1")).thenReturn(parsedToken);
        when(authSessionCache.getByAccessJti("ajti_1")).thenReturn(payload);

        authApplicationService.logout("Bearer atk_1", "trace");

        verify(authSessionCache).revokeAccess("ajti_1");
        verify(authSessionCache).revokeRefresh("rjti_1");
    }

    @Test
    void UT_APP_AUTH_REFRESH_INVALID() {
        when(authTokenService.parseRefreshToken("bad")).thenReturn(new ParsedToken(1001L, "rjti_x", "REFRESH"));
        when(authSessionCache.getByRefreshJti("rjti_x")).thenReturn(null);

        assertThatThrownBy(() -> authApplicationService.refresh(new RefreshCommand("bad"), "trace"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Refresh token invalid or expired");
    }

    @Test
    void UT_APP_AUTH_ME_SUCCESS() {
        when(authTokenService.parseAccessToken("atk_1")).thenReturn(new ParsedToken(1001L, "ajti_1", "ACCESS"));
        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(1001L);
        payload.setEmail("author@penmate.ai");
        payload.setDisplayName("作者A");
        payload.setRoles(List.of());
        payload.setPermissions(List.of());
        when(authSessionCache.getByAccessJti("ajti_1")).thenReturn(payload);

        Map<String, Object> result = authApplicationService.me("Bearer atk_1");

        assertThat(result).containsEntry("id", 1001L).containsEntry("email", "author@penmate.ai");
    }

    @Test
    void refresh_rotates_the_durable_session_before_issuing_cached_tokens() {
        ParsedToken parsed = new ParsedToken(1001L, "old-refresh", "REFRESH");
        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(1001L);
        payload.setSessionId("session-1");
        payload.setAccessJti("old-access");
        payload.setRefreshJti("old-refresh");
        payload.setAuthorizationVersion(3L);
        java.time.Instant accessExpiry = java.time.Instant.now().plusSeconds(1800);
        java.time.Instant refreshExpiry = java.time.Instant.now().plusSeconds(604800);
        AuthTokenBundle bundle = new AuthTokenBundle(
                "new-at", "new-rt", "new-access", "new-refresh", accessExpiry, refreshExpiry);
        when(authTokenService.parseRefreshToken("old-token")).thenReturn(parsed);
        when(authSessionCache.getByRefreshJti("old-refresh")).thenReturn(payload);
        when(iamGateway.findAuthorizationVersion(1001L)).thenReturn(3L);
        when(authTokenService.issueTokens(payload)).thenReturn(bundle);
        when(authSessions.rotate(eq("session-1"), eq(1001L),
                eq(AuthTokenFingerprint.sha256("old-refresh")), eq("new-access"),
                eq(AuthTokenFingerprint.sha256("new-refresh")), eq("127.0.0.1"),
                eq(refreshExpiry), any())).thenReturn(1);

        Map<String, Object> result = authApplicationService.refresh(
                new RefreshCommand("old-token", "127.0.0.1"), "trace-refresh");

        assertThat(result).containsEntry("accessToken", "new-at").containsEntry("refreshToken", "new-rt");
        verify(authSessionCache).revokeRefresh("old-refresh");
        verify(authSessionCache).revokeAccess("old-access");
        verify(authSessionCache).saveSession(payload, bundle);
    }

    @Test
    void UT_APP_AUTH_UPDATE_PROFILE_PERSISTS_AND_REFRESHES_SESSION() {
        when(authTokenService.parseAccessToken("atk_1")).thenReturn(new ParsedToken(1001L, "ajti_1", "ACCESS"));
        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(1001L);
        payload.setEmail("writer@example.com");
        payload.setRoles(List.of());
        payload.setPermissions(List.of());
        payload.setAuthorizationVersion(2L);
        when(authSessionCache.getByAccessJti("ajti_1")).thenReturn(payload);
        when(iamGateway.findAuthorizationVersion(1001L)).thenReturn(2L);

        IamUser user = new IamUser();
        user.setId(99L);
        user.setUserId(1001L);
        user.setEmail("writer@example.com");
        when(iamGateway.findUserByUserId(1001L)).thenReturn(user);
        when(iamGateway.updateOwnProfile(user)).thenReturn(1);

        Map<String, Object> result = authApplicationService.updateProfile(
                "Bearer atk_1", "  作者乙  ", "  简介  ");

        assertThat(user.getDisplayName()).isEqualTo("作者乙");
        assertThat(user.getEmail()).isEqualTo("writer@example.com");
        assertThat(user.getBio()).isEqualTo("简介");
        assertThat(result)
                .containsEntry("displayName", "作者乙")
                .containsEntry("email", "writer@example.com")
                .containsEntry("bio", "简介");
        verify(authSessionCache).updateSessionPayload("ajti_1", payload);
        verify(iamGateway, never()).updateEmail(any(), anyString());
    }

    @Test
    void revoke_other_sessions_retains_the_current_session_and_clears_other_cached_tokens() {
        when(authTokenService.parseAccessToken("atk_1"))
                .thenReturn(new ParsedToken(1001L, "current-access", "ACCESS"));
        AuthUserSessionPayload currentPayload = new AuthUserSessionPayload();
        currentPayload.setSessionId("current-session");
        when(authSessionCache.getByAccessJti("current-access")).thenReturn(currentPayload);

        AuthSession other = authSession("other-session", "other-access", "other-refresh");
        when(authSessions.revokeAllExcept(eq(1001L), eq("current-session"), any()))
                .thenReturn(List.of(other));

        assertThat(authApplicationService.revokeOtherSessions("Bearer atk_1")).isEqualTo(1);

        verify(authSessionCache).revokeAccess("other-access");
        verify(authSessionCache).revokeRefreshFingerprint("other-refresh");
        verify(authSessionCache, never()).revokeAccess("current-access");
        verify(authSessionCache, never()).revokeRefreshFingerprint("current-refresh");
    }

    @Test
    void get_ui_preferences_returns_defaults_when_the_user_has_not_saved_any() {
        when(authTokenService.parseAccessToken("atk_1")).thenReturn(new ParsedToken(1001L, "ajti_1", "ACCESS"));
        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(1001L);
        when(authSessionCache.getByAccessJti("ajti_1")).thenReturn(payload);
        when(uiPreferences.findByUserId(1001L)).thenReturn(null);

        UserUiPreferences result = authApplicationService.getUiPreferences("Bearer atk_1");

        assertThat(result.getThemeMode()).isEqualTo("SYSTEM");
        assertThat(result.getEditorFontFamily()).isEqualTo("SERIF");
        assertThat(result.getEditorFontSize()).isEqualTo(17);
        assertThat(result.getEditorContentWidth()).isEqualTo(760);
        assertThat(result.getHighlightCurrentParagraph()).isTrue();
    }

    @Test
    void save_ui_preferences_uses_the_authenticated_user_and_returns_persisted_values() {
        when(authTokenService.parseAccessToken("atk_1")).thenReturn(new ParsedToken(1001L, "ajti_1", "ACCESS"));
        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(1001L);
        when(authSessionCache.getByAccessJti("ajti_1")).thenReturn(payload);
        when(uiPreferences.upsert(any())).thenReturn(1);
        when(uiPreferences.findByUserId(1001L)).thenAnswer(invocation -> {
            UserUiPreferences result = new UserUiPreferences();
            result.setUserId(1001L);
            result.setThemeMode("DARK");
            return result;
        });
        UserUiPreferences input = new UserUiPreferences();
        input.setUserId(9999L);
        input.setThemeMode("dark");
        input.setEditorFontFamily("sans");
        input.setEditorFontSize(18);
        input.setEditorLineHeight(new java.math.BigDecimal("2.00"));
        input.setEditorParagraphSpacing(new java.math.BigDecimal("0.50"));
        input.setEditorContentWidth(800);
        input.setTypewriterMode(true);
        input.setHighlightCurrentParagraph(false);

        UserUiPreferences result = authApplicationService.saveUiPreferences("Bearer atk_1", input);

        assertThat(input.getUserId()).isEqualTo(1001L);
        assertThat(input.getThemeMode()).isEqualTo("DARK");
        assertThat(input.getEditorFontFamily()).isEqualTo("SANS");
        assertThat(result.getThemeMode()).isEqualTo("DARK");
        verify(uiPreferences).upsert(input);
    }

    private AuthSession authSession(String sessionId, String accessJti, String refreshJti) {
        AuthSession session = new AuthSession();
        session.setSessionId(sessionId);
        session.setCurrentAccessJti(accessJti);
        session.setCurrentRefreshJtiHash(refreshJti);
        return session;
    }
}

