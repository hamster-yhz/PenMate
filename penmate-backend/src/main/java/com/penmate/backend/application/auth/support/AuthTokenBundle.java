package com.penmate.backend.application.auth.support;

import java.time.Instant;

public record AuthTokenBundle(
        String accessToken,
        String refreshToken,
        String accessJti,
        String refreshJti,
        Instant accessExpiresAt,
        Instant refreshExpiresAt
) {
}

