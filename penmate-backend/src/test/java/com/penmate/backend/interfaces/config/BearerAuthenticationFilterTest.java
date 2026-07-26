package com.penmate.backend.interfaces.config;

import com.penmate.backend.application.auth.support.AuthSessionCache;
import com.penmate.backend.application.auth.support.AuthTokenService;
import com.penmate.backend.application.auth.support.AuthUserSessionPayload;
import com.penmate.backend.application.auth.support.ParsedToken;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BearerAuthenticationFilterTest {

    private final AuthTokenService authTokenService = mock(AuthTokenService.class);
    private final AuthSessionCache authSessionCache = mock(AuthSessionCache.class);
    private final IamGateway iamGateway = mock(IamGateway.class);
    private final BearerAuthenticationFilter filter = new BearerAuthenticationFilter(authTokenService, authSessionCache, iamGateway);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesBearerTokenWithSessionAuthorities() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/novels");
        request.addHeader("Authorization", "Bearer access-token");
        AuthUserSessionPayload session = sessionPayload();
        when(authTokenService.parseAccessToken("access-token"))
                .thenReturn(new ParsedToken(1001L, "access-jti", "ACCESS"));
        when(authSessionCache.getByAccessJti("access-jti")).thenReturn(session);
        when(iamGateway.findAuthorizationVersion(1001L)).thenReturn(3L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("1001");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ADMIN", "novel:read");
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/novels");
        request.addHeader("Authorization", "Bearer invalid-token");
        when(authTokenService.parseAccessToken("invalid-token")).thenThrow(new IllegalArgumentException("invalid"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejects_a_session_when_its_authorization_version_is_stale() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/novels");
        request.addHeader("Authorization", "Bearer access-token");
        AuthUserSessionPayload session = sessionPayload();
        when(authTokenService.parseAccessToken("access-token"))
                .thenReturn(new ParsedToken(1001L, "access-jti", "ACCESS"));
        when(authSessionCache.getByAccessJti("access-jti")).thenReturn(session);
        when(iamGateway.findAuthorizationVersion(1001L)).thenReturn(4L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private AuthUserSessionPayload sessionPayload() {
        AuthUserSessionPayload session = new AuthUserSessionPayload();
        session.setUserId(1001L);
        session.setAuthorizationVersion(3L);
        session.setRoles(List.of(Map.of("code", "ADMIN")));
        session.setPermissions(List.of(Map.of("code", "novel:read")));
        return session;
    }
}
