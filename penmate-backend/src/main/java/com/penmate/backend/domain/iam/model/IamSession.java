package com.penmate.backend.domain.iam.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class IamSession {
    private Long id;
    private Long userId;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime accessExpiresAt;
    private LocalDateTime refreshExpiresAt;
    private LocalDateTime revokedAt;

}

