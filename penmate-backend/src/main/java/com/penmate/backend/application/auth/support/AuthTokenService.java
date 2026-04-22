package com.penmate.backend.application.auth.support;

import com.penmate.backend.application.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Component
public class AuthTokenService {

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_USER_ID = "userId";

    private final SecretKey key;
    private final String issuer;
    private final long accessTokenExpireMinutes;
    private final long refreshTokenExpireDays;

    public AuthTokenService(@Value("${security.jwt.secret}") String secret,
                            @Value("${security.jwt.issuer}") String issuer,
                            @Value("${security.jwt.access-token-expire-minutes}") long accessTokenExpireMinutes,
                            @Value("${security.jwt.refresh-token-expire-days}") long refreshTokenExpireDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTokenExpireMinutes = accessTokenExpireMinutes;
        this.refreshTokenExpireDays = refreshTokenExpireDays;
    }

    public AuthTokenBundle issueTokens(AuthUserSessionPayload payload) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime accessExpiresAt = now.plusMinutes(accessTokenExpireMinutes);
        LocalDateTime refreshExpiresAt = now.plusDays(refreshTokenExpireDays);
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        String accessToken = buildToken(payload.getUserId(), accessJti, "ACCESS", accessExpiresAt);
        String refreshToken = buildToken(payload.getUserId(), refreshJti, "REFRESH", refreshExpiresAt);

        return new AuthTokenBundle(accessToken, refreshToken, accessJti, refreshJti, accessExpiresAt, refreshExpiresAt);
    }

    public ParsedToken parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!"ACCESS".equals(tokenType)) {
            throw BusinessException.unauthorized("Invalid access token");
        }
        return new ParsedToken(claims.get(CLAIM_USER_ID, Long.class), claims.getId(), tokenType);
    }

    public ParsedToken parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!"REFRESH".equals(tokenType)) {
            throw BusinessException.unauthorized("Invalid refresh token");
        }
        return new ParsedToken(claims.get(CLAIM_USER_ID, Long.class), claims.getId(), tokenType);
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception ex) {
            throw BusinessException.unauthorized("Token invalid or expired");
        }
    }

    private String buildToken(Long userId, String jti, String tokenType, LocalDateTime expiresAt) {
        Date now = new Date();
        Date exp = Date.from(expiresAt.toInstant(ZoneOffset.UTC));
        return Jwts.builder()
                .id(jti)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(exp)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .signWith(key)
                .compact();
    }
}

