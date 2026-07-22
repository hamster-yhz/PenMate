package com.penmate.backend.application.auth.support;

public interface AuthTokenService {

    AuthTokenBundle issueTokens(AuthUserSessionPayload payload);

    ParsedToken parseAccessToken(String token);

    ParsedToken parseRefreshToken(String token);
}

