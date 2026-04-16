package com.penmate.backend.application.auth;

import com.penmate.backend.application.auth.command.LoginCommand;
import com.penmate.backend.application.auth.command.RefreshCommand;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.iam.model.IamSession;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
        when(iamGateway.findUserByEmail("author@penmate.ai")).thenReturn(user);
        when(iamGateway.insertSession(any())).thenAnswer(invocation -> {
            IamSession s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        });

        Map<String, Object> result = authApplicationService.login(new LoginCommand("author@penmate.ai", "StrongPass!23"), traceId);

        verify(iamGateway).touchLastLogin(1001L);
        verify(auditService).write(eq(traceId), eq(1001L), eq("auth"), eq("login"), eq("iam_user_sessions"), eq("1"), eq("author@penmate.ai"), eq(200));
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
        IamSession session = new IamSession();
        session.setId(11L);
        session.setUserId(1001L);
        when(iamGateway.findSessionByAccessToken("atk_1")).thenReturn(session);

        authApplicationService.logout("Bearer atk_1", "trace");

        verify(iamGateway).revokeByAccessToken("atk_1");
        verify(auditService).write(eq("trace"), eq(1001L), eq("auth"), eq("logout"), eq("iam_user_sessions"), eq("11"), isNull(), eq(200));
    }

    @Test
    void UT_APP_AUTH_REFRESH_INVALID() {
        when(iamGateway.findSessionByRefreshToken("bad")).thenReturn(null);

        assertThatThrownBy(() -> authApplicationService.refresh(new RefreshCommand("bad"), "trace"))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Refresh token invalid or expired");
    }

    @Test
    void UT_APP_AUTH_ME_SUCCESS() {
        IamSession session = new IamSession();
        session.setUserId(1001L);
        session.setAccessExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(iamGateway.findSessionByAccessToken("atk_1")).thenReturn(session);
        IamUser user = new IamUser();
        user.setId(1001L);
        user.setEmail("author@penmate.ai");
        user.setDisplayName("作者A");
        when(iamGateway.findUserById(1001L)).thenReturn(user);
        when(iamGateway.findRolesByUserId(1001L)).thenReturn(List.of());
        when(iamGateway.findPermissionsByUserId(1001L)).thenReturn(List.of());

        Map<String, Object> result = authApplicationService.me("Bearer atk_1");

        assertThat(result).containsEntry("id", 1001L).containsEntry("email", "author@penmate.ai");
    }
}

