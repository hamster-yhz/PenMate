package com.penmate.backend.interfaces.config;

import com.penmate.backend.application.auth.support.AuthSessionCache;
import com.penmate.backend.application.auth.support.AuthTokenService;
import com.penmate.backend.application.auth.support.AuthUserSessionPayload;
import com.penmate.backend.application.auth.support.ParsedToken;
import com.penmate.backend.domain.iam.repository.IamGateway;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class BearerAuthenticationFilter extends OncePerRequestFilter {

    private final AuthTokenService authTokenService;
    private final AuthSessionCache authSessionCache;
    private final IamGateway iamGateway;

    public BearerAuthenticationFilter(AuthTokenService authTokenService,
                                      AuthSessionCache authSessionCache,
                                      IamGateway iamGateway) {
        this.authTokenService = authTokenService;
        this.authSessionCache = authSessionCache;
        this.iamGateway = iamGateway;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            ParsedToken parsed = authTokenService.parseAccessToken(token);
            AuthUserSessionPayload session = authSessionCache.getByAccessJti(parsed.tokenId());
            if (session == null || !Objects.equals(parsed.userId(), session.getUserId())) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access session is missing or revoked");
                return;
            }
            Long currentAuthorizationVersion = iamGateway.findAuthorizationVersion(session.getUserId());
            if (currentAuthorizationVersion == null
                    || !currentAuthorizationVersion.equals(session.getAuthorizationVersion())) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authorization changed; sign in again");
                return;
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    String.valueOf(session.getUserId()), null, authorities(session));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (DataAccessException error) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authentication session store unavailable");
        } catch (RuntimeException error) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired access token");
        }
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    private List<SimpleGrantedAuthority> authorities(AuthUserSessionPayload session) {
        List<SimpleGrantedAuthority> result = new ArrayList<>();
        addAuthorities(result, session.getRoles());
        addAuthorities(result, session.getPermissions());
        return result;
    }

    private void addAuthorities(List<SimpleGrantedAuthority> target, List<Map<String, Object>> values) {
        if (values == null) return;
        for (Map<String, Object> value : values) {
            Object code = value.get("code");
            if (code != null && !String.valueOf(code).isBlank()) {
                target.add(new SimpleGrantedAuthority(String.valueOf(code)));
            }
        }
    }
}
