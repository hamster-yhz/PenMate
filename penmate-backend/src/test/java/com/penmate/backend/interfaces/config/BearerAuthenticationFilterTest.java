package com.penmate.backend.interfaces.config;

import com.penmate.backend.application.auth.support.AuthSessionCache;
import com.penmate.backend.application.auth.support.AuthTokenService;
import com.penmate.backend.application.auth.support.AuthUserSessionPayload;
import com.penmate.backend.application.auth.support.ParsedToken;
import jakarta.servlet.http.Cookie;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BearerAuthenticationFilterTest {

    private final AuthTokenService authTokenService = mock(AuthTokenService.class);
    private final AuthSessionCache authSessionCache = mock(AuthSessionCache.class);
    private final BearerAuthenticationFilter filter = new BearerAuthenticationFilter(authTokenService, authSessionCache);

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

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("1001");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ADMIN", "novel:read");
    }

    @Test
    void acceptsAccessCookieOnlyForAgentRunStream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/v1/novels/novel-1/agent/runs/run-1/stream");
        request.setCookies(new Cookie("penmate_access", "stream-token"));
        when(authTokenService.parseAccessToken("stream-token"))
                .thenReturn(new ParsedToken(1001L, "stream-jti", "ACCESS"));
        when(authSessionCache.getByAccessJti("stream-jti")).thenReturn(sessionPayload());

        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(authTokenService).parseAccessToken("stream-token");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
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

    private AuthUserSessionPayload sessionPayload() {
        AuthUserSessionPayload session = new AuthUserSessionPayload();
        session.setUserId(1001L);
        session.setRoles(List.of(Map.of("code", "ADMIN")));
        session.setPermissions(List.of(Map.of("code", "novel:read")));
        return session;
    }
}
