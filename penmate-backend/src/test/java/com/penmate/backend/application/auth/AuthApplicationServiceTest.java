package com.penmate.backend.application.auth;

import com.penmate.backend.application.auth.command.LoginCommand;
import com.penmate.backend.application.auth.command.RefreshCommand;
import com.penmate.backend.application.auth.support.AuthSessionCache;
import com.penmate.backend.application.auth.support.AuthTokenBundle;
import com.penmate.backend.application.auth.support.AuthTokenService;
import com.penmate.backend.application.auth.support.AuthUserSessionPayload;
import com.penmate.backend.application.auth.support.ParsedToken;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
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

    @InjectMocks
    private AuthApplicationService authApplicationService;

    @Test
    void UT_APP_AUTH_LOGIN_SUCCESS() {
        String traceId = "UT-TRACE-AUTH-LOGIN-SUCCESS";
        IamUser user = new IamUser();
        user.setId(1001L);
        user.setEmail("author@penmate.ai");
        user.setStatus(1);
        user.setPasswordHash("StrongPass!23");
        user.setMainAgentModelConfigId(9001L);
        user.setDirtyWorkAgentModelConfigId(9002L);
        when(iamGateway.findUserByEmail("author@penmate.ai")).thenReturn(user);
        when(passwordEncoder.matches("StrongPass!23", "StrongPass!23")).thenReturn(true);
        when(iamGateway.findRolesByUserId(1001L)).thenReturn(List.of());
        when(iamGateway.findPermissionsByUserId(1001L)).thenReturn(List.of());
        when(authTokenService.issueTokens(any(AuthUserSessionPayload.class))).thenReturn(new AuthTokenBundle(
                "atk_1", "rtk_1", "ajti_1", "rjti_1", null, null
        ));

        Map<String, Object> result = authApplicationService.login(new LoginCommand("author@penmate.ai", "StrongPass!23"), traceId);

        ArgumentCaptor<AuthUserSessionPayload> payloadCaptor = ArgumentCaptor.forClass(AuthUserSessionPayload.class);
        verify(authSessionCache).saveSession(payloadCaptor.capture(), any(AuthTokenBundle.class));
        verify(iamGateway).touchLastLoginByUserId(1001L);
        assertThat(payloadCaptor.getValue().getMainAgentModelConfigId()).isNotNull();
        assertThat(payloadCaptor.getValue().getDirtyWorkAgentModelConfigId()).isNotNull();
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
}

