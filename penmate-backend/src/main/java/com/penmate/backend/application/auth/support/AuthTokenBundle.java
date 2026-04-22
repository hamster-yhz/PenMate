package com.penmate.backend.application.auth.support;

import java.time.LocalDateTime;

public record AuthTokenBundle(
        String accessToken,
        String refreshToken,
        String accessJti,
        String refreshJti,
        LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt
) {
}

